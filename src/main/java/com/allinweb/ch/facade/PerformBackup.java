package com.allinweb.ch.facade;

import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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

    public ErrorMessage backupHomeBanking(Connection conn, String backupFilePath, Integer homeBankId) {

        StringBuilder query = new StringBuilder(
                """
                SELECT id, url, name, priority, search_config, options_config,
                       cookies, driver_session, username, password
                FROM home_banking
                WHERE 1=1
                """);

        List<Object> parameters = new ArrayList<>();

        if (homeBankId != null) {
            query.append(" AND id = ?");
            parameters.add(homeBankId);
        }

        query.append(" ORDER BY id ASC");

        File sqlFile = new File(backupFilePath);

        try (PreparedStatement pstmt = conn.prepareStatement(query.toString());
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(sqlFile), Charset.forName("windows-1252")))) {

            // Set parameters dynamically
            for (int i = 0; i < parameters.size(); i++) {
                pstmt.setObject(i + 1, parameters.get(i));
            }

            try (ResultSet rs = pstmt.executeQuery()) {

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

                    writer.write(insert);
                    writer.newLine();
                }

                writer.flush();
                log.info("HomeBanking backup completed at: " + sqlFile.getAbsolutePath());
                return null;
            }

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

    public ErrorMessage backupBotJob(Connection conn, String backupFilePath, Integer homeBankingId, Integer botJobId) {

        StringBuilder query = new StringBuilder(
                """
            SELECT id, name, description, priority,
                   home_banking_id, home_url_id, active
            FROM bot_job
            WHERE 1=1
            """);

        List<Object> parameters = new ArrayList<>();

        if (botJobId != null) {
            query.append(" AND id = ?");
            parameters.add(botJobId);
        }

        if (homeBankingId != null) {
            query.append(" AND home_banking_id = ?");
            parameters.add(homeBankingId);
        }

        query.append(" ORDER BY id ASC");

        File sqlFile = new File(backupFilePath);

        try (PreparedStatement pstmt = conn.prepareStatement(query.toString());
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(sqlFile), Charset.forName("windows-1252")))) {

            // Set parameters dynamically
            for (int i = 0; i < parameters.size(); i++) {
                pstmt.setObject(i + 1, parameters.get(i));
            }

            try (ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    int id = rs.getInt("id");
                    String name = toSqlValue(rs.getString("name"));
                    String description = toSqlValue(rs.getString("description"));
                    String priority = toSqlValue(rs.getString("priority"));

                    int hbId = rs.getInt("home_banking_id");
                    boolean hbIdWasNull = rs.wasNull();

                    int homeUrlId = rs.getInt("home_url_id");
                    boolean homeUrlIdWasNull = rs.wasNull();

                    boolean active = rs.getBoolean("active");

                    String insert = String.format(
                            "INSERT INTO bot_job (id, name, description, priority, home_banking_id, home_url_id, active) "
                                    + "VALUES (%d, '%s', '%s', '%s', %s, %s, %d);",
                            id,
                            name,
                            description,
                            priority,
                            hbIdWasNull ? "NULL" : hbId,
                            homeUrlIdWasNull ? "NULL" : homeUrlId,
                            active ? 1 : 0);

                    writer.write(insert);
                    writer.newLine();
                }

                writer.flush();
                log.info("Bot_job backup completed at: " + sqlFile.getAbsolutePath());
                return null;
            }

        } catch (Exception error) {
            log.error("Error during bot_job backup: " + error.getMessage());
            return new ErrorMessage("Error in backup process", "Error during bot_job backup", error.getMessage());
        }
    }

    public ErrorMessage backupInstruction(Connection conn, String backupFilePath, Integer botJobIdFilter) {
        String query =
                """
                        SELECT id, instruction_order_number, actions, name, xpath, coordinates,
                               force_coordinates, iframe_xpath, tag_name, shadow_host, shadow_root,
                               css_selector, description, operation, optional, block_marked, default_value,
                               action_custom_max_wait_sec, on_hold_seconds, codified, export_to_abr,
                               active, block_id, variable_id, parent_block_id, parent_id, bot_job_id
                        FROM instruction
                        """
                        + (botJobIdFilter != null ? " WHERE bot_job_id = ? " : "")
                        + """
                                ORDER BY id ASC
                                """;

        File sqlFile = new File(backupFilePath);
        try (PreparedStatement pstmt = conn.prepareStatement(query);
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(sqlFile), Charset.forName("windows-1252")))) {

            if (botJobIdFilter != null) {
                pstmt.setInt(1, botJobIdFilter);
            }

            try (ResultSet rs = pstmt.executeQuery()) { // <-- moved ResultSet into try (no logic removed)
                while (rs.next()) {
                    int id = rs.getInt("id");
                    int order = rs.getInt("instruction_order_number");
                    String actions = toSqlValue(rs.getString("actions"));
                    String name = toSqlValue(rs.getString("name"));
                    String xpath = toSqlValue(rs.getString("xpath"));
                    String coordinates = toSqlValue(rs.getString("coordinates"));
                    String forceCoordinates = toSqlValue(rs.getString("force_coordinates"));
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
                    String clientNamed = toSqlValue(rs.getString("client_named"));

                    String insert = String.format(
                            "INSERT INTO instruction (id, instruction_order_number, actions, name, xpath, coordinates, force_coordinates, iframe_xpath, tag_name, shadow_host, shadow_root, css_selector, description, operation, optional, block_marked, default_value, action_custom_max_wait_sec, on_hold_seconds, codified, export_to_abr, active, block_id, variable_id, parent_block_id, parent_id, bot_job_id, client_named) "
                                    + "VALUES (%d, %d, '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', %d, %d, '%s', %s, %s, %d, %d, %d, %s, %s, %s, %s, %s, '%s');",
                            id,
                            order,
                            actions,
                            name,
                            xpath,
                            coordinates,
                            forceCoordinates,
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
                            botJobIdWasNull ? "NULL" : botJobId,
                            clientNamed);

                    writer.write(insert + System.lineSeparator());
                }
            }

            writer.flush();

            log.info("Instruction backup completed at: " + sqlFile.getAbsolutePath());
            return null;

        } catch (Exception error) {

            log.error("Error during instruction backup: " + error.getMessage());
            return new ErrorMessage("Error in backup process", "Error during instruction backup", error.getMessage());
        }
    }

    public ErrorMessage backupVariable(Connection conn, String backupFilePath, Integer botJobIdFilter) {
        String query =
                """
                        SELECT id, type, name, value, instruction_id, bot_job_id, local_format, delimiter
                        FROM variable
                        """
                        + (botJobIdFilter != null ? " WHERE bot_job_id = ? " : "")
                        + """
                                ORDER BY id ASC
                                """;

        File sqlFile = new File(backupFilePath);
        try (PreparedStatement pstmt = conn.prepareStatement(query);
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(sqlFile), Charset.forName("windows-1252")))) {

            if (botJobIdFilter != null) {
                pstmt.setInt(1, botJobIdFilter);
            }

            try (ResultSet rs = pstmt.executeQuery()) { // moved inside try (same as previous pattern)

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
            }

            writer.flush();

            log.info("Variable backup completed at: " + sqlFile.getAbsolutePath());
            return null;

        } catch (Exception error) {
            log.error("Error during variable backup: " + error.getMessage());
            return new ErrorMessage("Error in backup process", "Error during variable backup", error.getMessage());
        }
    }

    public ErrorMessage backupReference(Connection conn, String backupFilePath, Integer botJobIdFilter) {
        String query =
                """
                        SELECT id, reference_type, value, instruction_id, bot_job_id
                        FROM reference
                        """
                        + (botJobIdFilter != null ? " WHERE bot_job_id = ? " : "")
                        + """
                                ORDER BY id ASC
                                """;

        File sqlFile = new File(backupFilePath);
        try (PreparedStatement pstmt = conn.prepareStatement(query);
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(sqlFile), Charset.forName("windows-1252")))) {

            if (botJobIdFilter != null) {
                pstmt.setInt(1, botJobIdFilter);
            }

            try (ResultSet rs = pstmt.executeQuery()) { // moved inside try (same safe pattern)

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
            }

            writer.flush();

            log.info("Reference backup completed at: " + sqlFile.getAbsolutePath());
            return null;

        } catch (Exception error) {
            log.error("Error during reference backup: " + error.getMessage());
            return new ErrorMessage("Error in backup process", "Error during reference backup", error.getMessage());
        }
    }

    public ErrorMessage backupBlock(Connection conn, String backupFilePath, Integer botJobId) {

        StringBuilder query = new StringBuilder(
                """
            SELECT id, block_order_number, name, description,
                   type_id, export_file, active, wait, bot_job_id
            FROM block
            WHERE 1=1
            """);

        if (botJobId != null) {
            query.append(" AND bot_job_id = ?");
        }

        query.append(" ORDER BY id ASC");

        File sqlFile = new File(backupFilePath);

        try (PreparedStatement pstmt = conn.prepareStatement(query.toString());
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(sqlFile), Charset.forName("windows-1252")))) {

            if (botJobId != null) {
                pstmt.setInt(1, botJobId);
            }

            try (ResultSet rs = pstmt.executeQuery()) {

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

                    int botJobIdValue = rs.getInt("bot_job_id");
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
                            botJobIdWasNull ? "NULL" : botJobIdValue);

                    writer.write(insert);
                    writer.newLine();
                }

                writer.flush();
                log.info("Block backup completed at: " + sqlFile.getAbsolutePath());
                return null;
            }

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

                String forceCoordinatesRaw = rs.getString("force_coordinates");
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
                String clientNamed = toSqlValue(rs.getString("client_named"));

                String insert = String.format(
                        "INSERT INTO component_instruction ("
                                + "id, instruction_order_number, actions, name, xpath, coordinates, force_coordinates, iframe_xpath, "
                                + "tag_name, shadow_host, shadow_root, css_selector, description, operation, optional, block_marked, "
                                + "default_value, action_custom_max_wait_sec, on_hold_seconds, codified, export_to_abr, active, "
                                + "block_id, variable_id, parent_block_id, parent_id, home_banking_id, client_named"
                                + ") VALUES (%d, %d, '%s', '%s', '%s', '%s', %s, '%s', '%s', '%s', '%s', '%s', '%s', '%s', %s, %s, '%s', %s, %s, %s, %s, %d, %s, %s, %s, %s, %s, '%s');",
                        id,
                        orderNumber,
                        actions,
                        name,
                        xpath,
                        coordinates,
                        forceCoordinatesWasNull
                                ? "NULL"
                                : ("'" + (forceCoordinatesRaw == null ? "" : forceCoordinatesRaw) + "'"),
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
                        homeBankingIdWasNull ? "NULL" : homeBankingId,
                        clientNamed);

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

    public ErrorMessage getHomeBankingNameFromFile(String sqlFilePath, String currentOrganization) {

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(sqlFilePath), Charset.forName("windows-1252")))) {

            StringBuilder currentInsert = new StringBuilder();
            String line;

            String oldName = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                currentInsert.append(line);

                if (line.endsWith(";")) {
                    String insertSql = currentInsert.toString();
                    currentInsert.setLength(0);

                    List<String> values = extractValuesFromInsert(insertSql);

                    if (values.size() != 10) {
                        continue;
                    }

                    oldName = values.get(2); // name is index 2

                    // Normalize if needed (optional trim)
                    if (oldName != null) {
                        oldName = oldName.trim();
                    }

                    if (currentOrganization != null && currentOrganization.equalsIgnoreCase(oldName)) {

                        // Organization matches → OK
                        return null;
                    }
                }
            }

            // No match found
            return new ErrorMessage(
                    "Import Failed: Different organizations!",
                    "Attempt to import from: " + oldName + " to " + currentOrganization,
                    "You cannot import a Bot Job between different organizations.");

        } catch (Exception e) {

            log.error("Failed reading home_banking name from file: " + e.getMessage());

            return new ErrorMessage(
                    "Import Failed: File Not Found",
                    "Import attempt failed: " + sqlFilePath,
                    "The file was not found. Please execute the Export Bot Job first or select the correct directory.");
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
                            default -> log.error("Unexpected value index: " + i);
                        }
                    }

                    // Track old ID for mapping later
                    try {
                        int oldId = Integer.parseInt(values.get(0));
                        insertedOldIds.add(oldId);
                        homeBankMap.put(oldId, -1); // initially set to -1
                    } catch (Exception ex) {
                        log.info("Error parsing homeBankMap entry: " + ex.getMessage());
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
                log.error("Mismatch: inserted count " + insertedOldIds.size() + " vs new IDs " + newIds.size());
            }

            for (int i = 0; i < Math.min(insertedOldIds.size(), newIds.size()); i++) {
                homeBankMap.put(insertedOldIds.get(i), newIds.get(i));
            }

            log.info("HomeBankMap populated: " + homeBankMap);

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
                        log.info("Invalid home_banking_id format: " + values.get(2));
                    }

                    // Lookup newHomeBankId from homeBankMap
                    Integer newHomeBankId = null;
                    if (oldHomeBankId != null) {
                        newHomeBankId = homeBankMap.get(oldHomeBankId);
                    }

                    if (newHomeBankId == null) {
                        log.info("Skipped home_url with unknown home_banking_id: " + oldHomeBankId);
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
                        log.info("Error parsing homeUrlMap entry: " + ex.getMessage());
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
                log.error("Mismatch: inserted count " + insertedOldIds.size() + " vs new IDs " + newIds.size());
            }

            for (int i = 0; i < Math.min(insertedOldIds.size(), newIds.size()); i++) {
                homeUrlMap.put(insertedOldIds.get(i), newIds.get(i));
            }

            log.info("homeUrlMap populated: " + homeUrlMap);

            return null;

        } catch (Exception e) {
            return new ErrorMessage("Restore Failed", "Failed to load home_url data", e.getMessage());
        }
    }

    public ErrorMessage restoreBotJob(
            Connection conn,
            String sqlFilePath,
            Integer homeBankIdImported,
            Integer homeUrlIdImported,
            Integer botJobIdImported) {
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

            String timeStamp = null;
            if (homeBankIdImported != null && homeBankIdImported > 0 && homeUrlIdImported != null) {

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd HHmmss");
                timeStamp = LocalDateTime.now().format(formatter);

                homeBankMap.clear();
                homeBankMap.put(homeBankIdImported, homeBankIdImported);
                homeUrlMap.clear();
                homeUrlMap.put(homeUrlIdImported, homeUrlIdImported);
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
                        oldHomeBankId = homeBankIdImported == null
                                ? parseIntSafe(values.get(4))
                                : homeBankIdImported; // The Passed homeBankIdImported will be the key
                    } catch (NumberFormatException ex) {
                        log.info("Invalid home_banking_id format: " + values.get(4));
                    }

                    // Extract old home_url_id (index 5)
                    Integer oldHomeUrlId = null;
                    try {
                        oldHomeUrlId = homeUrlIdImported == null
                                ? parseIntSafe(values.get(5))
                                : homeUrlIdImported; // The Passed homeUrlIdImported will be the key
                    } catch (NumberFormatException ex) {
                        log.info("Invalid home_url_id format: " + values.get(5));
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
                        log.info("Skipped bot_job with unknown home_banking_id: " + oldHomeBankId);
                        currentInsert.setLength(0);

                        if (homeBankIdImported != null) {
                            return new ErrorMessage(
                                    "Import Failed: Different organizations!",
                                    "Import attempt failed",
                                    "You cannot import a Bot Job between different organizations.");
                        }
                        continue; // skip this row
                    }

                    if (newHomeUrlId == null) {
                        log.info("Skipped bot_job with unknown home_url_id: " + oldHomeUrlId);
                        currentInsert.setLength(0);
                        continue; // skip this row
                    }

                    // Now set parameters
                    for (int i = 1; i < values.size(); i++) {
                        String val = values.get(i);

                        switch (i) {
                                //                            case 0 -> setSafeParam(pstmt, 1, val, Types.INTEGER); //
                                // id
                            case 1 -> {
                                if (!Strings.isNullOrEmpty(timeStamp)) {
                                    setSafeParam(pstmt, 1, val + " " + timeStamp, Types.VARCHAR);
                                } else {
                                    setSafeParam(pstmt, 1, val, Types.VARCHAR);
                                }
                            } // name
                            case 2 -> setSafeParam(pstmt, 2, val, Types.VARCHAR); // description
                            case 3 -> setSafeParam(pstmt, 3, val, Types.VARCHAR); // priority
                            case 4 -> pstmt.setInt(4, newHomeBankId); // home_banking_id (mapped)
                            case 5 -> pstmt.setInt(5, newHomeUrlId); // home_url_id (mapped)
                            case 6 -> setSafeParam(pstmt, 6, val, Types.INTEGER); // active
                        }
                    }

                    // Track old ID for mapping later
                    Integer oldId = null;
                    try {
                        oldId = botJobIdImported == null
                                ? parseIntSafe(values.get(0))
                                : botJobIdImported; // The Passed botJobId will be the key
                        insertedOldIds.add(oldId);
                        botJobMap.put(oldId, -1); // initialize mapping
                    } catch (NumberFormatException ex) {
                        log.info("Error parsing botJobMap entry: {}", ex.getMessage());
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
                log.error("Mismatch: inserted count " + insertedOldIds.size() + " vs new IDs " + newIds.size());
            }

            for (int i = 0; i < Math.min(insertedOldIds.size(), newIds.size()); i++) {
                botJobMap.put(insertedOldIds.get(i), newIds.get(i));
            }

            log.info("botJobMap populated: " + botJobMap);

            return null;

        } catch (Exception e) {
            return new ErrorMessage("Restore Failed", "Failed to load bot_job data", e.getMessage());
        }
    }

    public ErrorMessage restoreBlock(Connection conn, String sqlFilePath, Integer botJobIdImported) {
        String insertQuery =
                """
                        INSERT INTO block (
                            block_order_number, name, description, type_id,
                            export_file, active, wait, bot_job_id
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?);
                        """;

        String selectBlockIdsSQL = "SELECT id FROM block ";

        //        if (botJobIdImported != null) {
        //            Integer newBotJob = botJobMap.get(botJobIdImported);
        //            if (newBotJob != null) {
        //                selectBlockIdsSQL += " where bot_job_id = " + newBotJob;
        //            } else {
        //                return new ErrorMessage("Import Failed", "Failed to import blocks data", "New Bot Job Not
        // found");
        //            }
        //        }

        selectBlockIdsSQL += " ORDER BY id";

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
                        oldBotJobId = botJobIdImported == null
                                ? parseIntSafe(values.get(8))
                                : botJobIdImported; // The Passed botJobId will be the key
                    } catch (NumberFormatException ex) {
                        log.info("Invalid bot_job_id format: " + values.get(8));
                    }

                    // Lookup newBotJobId from botJobMap
                    Integer newBotJobId = null;
                    if (oldBotJobId != null) {
                        newBotJobId = botJobMap.get(oldBotJobId);
                    }

                    if (newBotJobId == null) {
                        log.info("Skipped block with unknown bot_job_id: " + oldBotJobId);
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
                        log.info("Error parsing blockMap entry: " + ex.getMessage());
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
                log.error("Mismatch: inserted count " + insertedOldIds.size() + " vs new IDs " + newIds.size());
            }

            for (int i = 0; i < Math.min(insertedOldIds.size(), newIds.size()); i++) {
                blockMap.put(insertedOldIds.get(i), newIds.get(i));
            }

            log.info("blockMap populated: " + blockMap);

            return null;

        } catch (Exception e) {
            return new ErrorMessage("Restore Failed", "Failed to load block data", e.getMessage());
        }
    }

    public ErrorMessage restoreInstruction(Connection conn, String sqlFilePath, Integer botJobIdImported) {
        String insertQuery =
                """
                        INSERT INTO instruction (
                            instruction_order_number, actions, name, xpath, coordinates,
                            force_coordinates, iframe_xpath, tag_name, shadow_host, shadow_root,
                            css_selector, description, operation, optional, block_marked,
                            default_value, action_custom_max_wait_sec, on_hold_seconds, codified,
                            export_to_abr, active, block_id, variable_id, parent_block_id, parent_id, bot_job_id,
                            client_named
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                        """;

        String selectInstructionIdsSQL = "SELECT id FROM instruction ";

        //        if (botJobIdImported != null) {
        //            Integer newBotJob = botJobMap.get(botJobIdImported);
        //            if (newBotJob != null) {
        //                selectInstructionIdsSQL += " where bot_job_id = " + newBotJob;
        //            } else {
        //                return new ErrorMessage("Import Failed", "Failed to import instructions data", "New Bot Job
        // Not found");
        //            }
        //        }

        selectInstructionIdsSQL += " ORDER BY id";

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

                    if (values.size() != 26 && values.size() != 27 && values.size() != 28) {
                        return new ErrorMessage(
                                "Parse Error",
                                "Expected 26, 27, or 28 values for instruction, but got " + values.size(),
                                currentInsert.toString());
                    }

                    // Extract old block_id (index 22) and bot_job_id (index 25 / index 26)
                    Integer oldBlockId = parseIntSafe(values.get(22));
                    Integer oldBotJobId = botJobIdImported == null
                            ? parseIntSafe(values.get(values.size() == 26 ? 25 : 26)) // index depends on 26/27 cols
                            : botJobIdImported; // The Passed botJobId will be the key

                    Integer newBlockId = oldBlockId != null ? blockMap.get(oldBlockId) : null;
                    Integer newBotJobId = oldBotJobId != null ? botJobMap.get(oldBotJobId) : null;

                    if (newBlockId == null) {
                        log.info("Skipped instruction with unknown block_id: " + oldBlockId);
                        currentInsert.setLength(0);
                        continue;
                    }

                    if (newBotJobId == null) {
                        log.info("Skipped instruction with unknown bot_job_id: " + oldBotJobId);
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
                    // client_named — Roadmap 3 Phase 3d. Pre-migration backups have 26/27 values; only the
                    // 28-value format includes client_named at index 27. Treat the legacy "[null]" / "null"
                    // sentinel produced by toSqlValue as a real NULL.
                    {
                        String clientNamedValue = values.size() >= 28 ? values.get(27) : null;
                        if (clientNamedValue != null
                                && !clientNamedValue.isBlank()
                                && !clientNamedValue.equalsIgnoreCase("null")
                                && !clientNamedValue.equalsIgnoreCase("[null]")) {
                            setSafeParam(pstmt, 27, clientNamedValue, Types.VARCHAR);
                        } else {
                            setSafeParam(pstmt, 27, null, Types.VARCHAR);
                        }
                    }

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
                            case 6 -> setSafeParam(pstmt, 6, values.get(6), Types.VARCHAR); // force_coordinates
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
                                setSafeParam(pstmt, 23, null, Types.INTEGER);
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
                        log.info("Error parsing instructionMap entry: " + ex.getMessage());
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
                log.error("Mismatch: inserted count " + insertedOldIds.size() + " vs new IDs " + newIds.size());
            }

            for (int i = 0; i < Math.min(insertedOldIds.size(), newIds.size()); i++) {
                instructionMap.put(insertedOldIds.get(i), newIds.get(i));
            }

            log.info("instructionMap populated: " + instructionMap);
            return null;

        } catch (Exception error) {
            return new ErrorMessage("Restore Failed", "Failed to load instruction data", error.getMessage());
        }
    }

    public ErrorMessage restoreVariable(Connection conn, String sqlFilePath, Integer botJobIdImported) {
        String insertQuery =
                """
                        INSERT INTO variable (
                            type, name, value, instruction_id, bot_job_id, local_format, delimiter
                        ) VALUES (?, ?, ?, ?, ?, ?, ?);
                        """;

        String selectVariableIdsSQL = "SELECT id FROM variable ";

        //        if (botJobIdImported != null) {
        //            Integer newBotJob = botJobMap.get(botJobIdImported);
        //            if (newBotJob != null) {
        //                selectVariableIdsSQL += " where bot_job_id = " + newBotJob;
        //            } else {
        //                return new ErrorMessage("Import Failed", "Failed to import variables data", "New Bot Job Not
        // found");
        //            }
        //        }

        selectVariableIdsSQL += " ORDER BY id";

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
                    try {
                        oldInstructionId = Integer.parseInt(values.get(4));
                    } catch (NumberFormatException ex) {
                        log.info("Invalid instruction_id format: " + values.get(4));
                    }
                    Integer oldBotJobId = null;
                    try {
                        oldBotJobId = botJobIdImported == null
                                ? parseIntSafe(values.get(5))
                                : botJobIdImported; // The Passed botJobId will be the key

                    } catch (NumberFormatException ex) {
                        log.info("Invalid bot_job_id format: " + values.get(5));
                    }

                    // Lookup new IDs
                    Integer newInstructionId = oldInstructionId != null ? instructionMap.get(oldInstructionId) : null;
                    Integer newBotJobId = oldBotJobId != null ? botJobMap.get(oldBotJobId) : null;

                    if (newInstructionId == null) {
                        log.info("Skipped variable with unknown instruction_id: " + oldInstructionId);
                        currentInsert.setLength(0);
                        continue;
                    }
                    if (newBotJobId == null) {
                        log.info("Skipped variable with unknown bot_job_id: " + oldBotJobId);
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
                        log.info("Error parsing variableMap entry: " + ex.getMessage());
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
                log.error("Mismatch: inserted count " + insertedOldIds.size() + " vs new IDs " + newIds.size());
            }

            for (int i = 0; i < Math.min(insertedOldIds.size(), newIds.size()); i++) {
                variableMap.put(insertedOldIds.get(i), newIds.get(i));
            }

            log.info("variableMap populated: " + variableMap);

            return null;

        } catch (Exception e) {
            return new ErrorMessage("Restore Failed", "Failed to load variable data", e.getMessage());
        }
    }

    public ErrorMessage restoreUpdateInstruction(Connection conn, Integer botJobIdImported) {
        final int BATCH_SIZE = 100;

        instrNewInverted.clear();

        for (Map.Entry<Integer, Integer> entry : instructionMap.entrySet()) {
            instrNewInverted.put(entry.getValue(), entry.getKey());
        }

        if (instrNewInverted.isEmpty()) {
            return null;
        }

        try (Statement connStmt = conn.createStatement()) {
            conn.setAutoCommit(false);

            String selectAccessSQL = "SELECT id, name, parent_id, variable_id, parent_block_id, bot_job_id "
                    + "FROM instruction WHERE (parent_id IS NULL OR variable_id IS NULL OR parent_block_id IS NULL)";

            if (botJobIdImported != null) {
                Integer newBotJob = botJobMap.get(botJobIdImported);
                if (newBotJob != null) {
                    selectAccessSQL += " AND bot_job_id = " + newBotJob;
                } else {
                    return new ErrorMessage(
                            "Import Failed", "Failed to update instruction data", "New Bot Job Not found");
                }
            }

            selectAccessSQL += " ORDER BY id";

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
                            log.info("Updated batch of " + BATCH_SIZE);
                        }
                    }

                    if (count % BATCH_SIZE != 0) {
                        updateStmt.executeBatch();
                        conn.commit();
                        log.info("Updated final batch of " + (count % BATCH_SIZE));
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

    public ErrorMessage restoreReference(Connection conn, String sqlFilePath, Integer botJobIdImported) {
        String insertQuery =
                """
                        INSERT INTO reference (
                            reference_type, value, instruction_id, bot_job_id
                        ) VALUES (?, ?, ?, ?);
                        """;

        String selectReferenceIdsSQL = "SELECT id FROM reference ";

        //        if (botJobIdImported != null) {
        //            Integer newBotJob = botJobMap.get(botJobIdImported);
        //            if (newBotJob != null) {
        //                selectReferenceIdsSQL += " where bot_job_id = " + newBotJob;
        //            } else {
        //                return new ErrorMessage("Import Failed", "Failed to import references data", "New Bot Job Not
        // found");
        //            }
        //        }

        selectReferenceIdsSQL += " ORDER BY id";

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
                    Integer oldBotJobId = botJobIdImported == null
                            ? parseIntSafe(values.get(4))
                            : botJobIdImported; // The Passed botJobId will be the key

                    Integer newInstructionId = oldInstructionId != null ? instructionMap.get(oldInstructionId) : null;
                    Integer newBotJobId = oldBotJobId != null ? botJobMap.get(oldBotJobId) : null;

                    if (newInstructionId == null || newBotJobId == null) {
                        log.info("Skipped reference due to unknown instruction or bot_job ID: " + "instr="
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
                        log.info("Error parsing referenceMap entry: " + ex.getMessage());
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
                log.error("Mismatch: inserted count " + insertedOldIds.size() + " vs new IDs " + newIds.size());
            }

            for (int i = 0; i < Math.min(insertedOldIds.size(), newIds.size()); i++) {
                referenceMap.put(insertedOldIds.get(i), newIds.get(i));
            }

            log.info("referenceMap populated: " + referenceMap);

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
                        log.info("Invalid home_banking_id format: " + values.get(1));
                    }

                    // Lookup newBotJobId from botJobMap
                    Integer newHomeBankId = null;
                    if (oldHomeBankId != null) {
                        newHomeBankId = homeBankMap.get(oldHomeBankId);
                    }

                    if (newHomeBankId == null) {
                        log.info("Skipped block with unknown home_banking_id: " + oldHomeBankId);
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
                        log.info("Error parsing componentBlockMap entry: " + ex.getMessage());
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
                log.error("Mismatch: inserted count " + insertedOldIds.size() + " vs new IDs " + newIds.size());
            }

            for (int i = 0; i < Math.min(insertedOldIds.size(), newIds.size()); i++) {
                blockMap.put(insertedOldIds.get(i), newIds.get(i));
            }

            log.info("componentBlockMap populated: " + blockMap);

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
                            export_to_abr, active, block_id, variable_id, parent_block_id, parent_id, home_banking_id,
                            client_named
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
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

                    if (values.size() != 26 && values.size() != 27 && values.size() != 28) {
                        return new ErrorMessage(
                                "Parse Error",
                                "Expected 26, 27, or 28 values for instruction, but got " + values.size(),
                                currentInsert.toString());
                    }

                    // Extract old block_id (index 22) and bot_job_id (index 25 / index 26)
                    Integer oldBlockId = parseIntSafe(values.get(22));
                    Integer oldHomeBankId =
                            parseIntSafe(values.get(values.size() == 26 ? 25 : 26)); // index depends on 26/27 cols

                    Integer newBlockId = oldBlockId != null ? blockMap.get(oldBlockId) : null;
                    Integer newHomeBankId = oldHomeBankId != null ? homeBankMap.get(oldHomeBankId) : null;

                    if (newBlockId == null) {
                        log.info("Skipped component_instruction with unknown block_id: " + oldBlockId);
                        currentInsert.setLength(0);
                        continue;
                    }
                    if (newHomeBankId == null) {
                        log.info("Skipped instruction with unknown home_banking_id: " + oldHomeBankId);
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
                    // client_named — Roadmap 3 Phase 3d. Pre-migration backups have 26/27 values; only the
                    // 28-value format includes client_named at index 27.
                    {
                        String clientNamedValue = values.size() >= 28 ? values.get(27) : null;
                        if (clientNamedValue != null
                                && !clientNamedValue.isBlank()
                                && !clientNamedValue.equalsIgnoreCase("null")
                                && !clientNamedValue.equalsIgnoreCase("[null]")) {
                            setSafeParam(pstmt, 27, clientNamedValue, Types.VARCHAR);
                        } else {
                            setSafeParam(pstmt, 27, null, Types.VARCHAR);
                        }
                    }

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
                            case 6 -> setSafeParam(pstmt, 6, values.get(6), Types.VARCHAR); // force_coordinates
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
                        log.info("Error parsing instructionMap entry: " + ex.getMessage());
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
                log.error("Mismatch: inserted count " + insertedOldIds.size() + " vs new IDs " + newIds.size());
            }

            for (int i = 0; i < Math.min(insertedOldIds.size(), newIds.size()); i++) {
                instructionMap.put(insertedOldIds.get(i), newIds.get(i));
            }

            log.info("instructionMap populated: " + instructionMap);
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
                        log.info("Skipped variable with unknown instruction_id: " + oldInstructionId);
                        currentInsert.setLength(0);
                        continue;
                    }
                    if (newHomeBankingId == null) {
                        log.info("Skipped variable with unknown home_banking_id: " + oldHomeBankingId);
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
                        log.info("Error parsing variableMap entry: " + ex.getMessage());
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
                log.error("Mismatch: inserted count " + insertedOldIds.size() + " vs new IDs " + newIds.size());
            }

            for (int i = 0; i < Math.min(insertedOldIds.size(), newIds.size()); i++) {
                variableMap.put(insertedOldIds.get(i), newIds.get(i));
            }

            log.info("variableMap populated: " + variableMap);

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
                            log.info("Updated batch of " + BATCH_SIZE);
                        }
                    }

                    if (count % BATCH_SIZE != 0) {
                        updateStmt.executeBatch();
                        conn.commit();
                        log.info("Updated final batch of " + (count % BATCH_SIZE));
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
                        log.info("Skipped component_reference due to unknown instruction or home_bank ID: " + "instr="
                                + oldInstructionId + ", homeBankId=" + oldHomeBankId);
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
                        log.info("Error parsing referenceMap entry: " + ex.getMessage());
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
                log.error("Mismatch: inserted count " + insertedOldIds.size() + " vs new IDs " + newIds.size());
            }

            for (int i = 0; i < Math.min(insertedOldIds.size(), newIds.size()); i++) {
                referenceMap.put(insertedOldIds.get(i), newIds.get(i));
            }

            log.info("referenceMap populated: " + referenceMap);

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

    // ─────────────────────────────────────────────────────────────────────────────
    //  One-file backup / restore (added 2026-04).
    //
    //  These two methods write and read a single .sql file that carries every row
    //  of every table, in FK-safe dependency order, using the same INSERT format
    //  as the legacy per-table backup files:
    //   - strings wrapped in '…' with '' to escape single-quotes; NULL strings
    //     serialised as the literal '[null]' (matching the legacy toSqlValue quirk)
    //   - integers written raw; NULL integers written as the bare keyword NULL
    //   - booleans written as 0 / 1 (schema stores them as INTEGER across all
    //     three dialects: Access, SQLite, Postgres)
    //   - one INSERT statement per row, terminated by ';' + line separator
    //
    //  The file is therefore a concatenation of the legacy backup_<table>.sql
    //  outputs; a legacy reader that only knows per-table files could still
    //  scan this file line-by-line.
    //
    //  IDs are preserved verbatim — every INSERT names the id column explicitly,
    //  so variable_id / block_id / parent_id / parent_block_id / bot_job_id /
    //  home_banking_id references stay valid after restore.
    //
    //  The existing per-table backupX / restoreX methods are left in place for
    //  callers that still rely on them; these new methods are purely additive.
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Descriptor for one table in the backup. The column order here defines the
     * column order written into every INSERT for that table and must match the
     * order used by the legacy per-table backup methods (so that existing backup
     * files remain format-compatible with this unified format).
     */
    private static final class BackupTableSpec {
        final String tableName;
        final List<String> columns;

        BackupTableSpec(String tableName, List<String> columns) {
            this.tableName = tableName;
            this.columns = columns;
        }
    }

    /**
     * Insert order: parents first. Respects every declared FK in the SQLite and
     * Postgres schemas. {@code variable} sits AFTER {@code instruction} because
     * its {@code instruction_id} column has a FK to instruction(id);
     * {@code instruction.variable_id} has no FK declared (on purpose — breaks the
     * would-be cycle), so there is nothing to patch up in a second pass.
     */
    private static final List<BackupTableSpec> BACKUP_TABLES_IN_ORDER = List.of(
            new BackupTableSpec(
                    "home_banking",
                    List.of(
                            "id",
                            "url",
                            "name",
                            "priority",
                            "search_config",
                            "options_config",
                            "cookies",
                            "driver_session",
                            "username",
                            "password")),
            new BackupTableSpec("home_url", List.of("id", "url", "home_banking_id")),
            new BackupTableSpec(
                    "bot_job",
                    List.of("id", "name", "description", "priority", "home_banking_id", "home_url_id", "active")),
            new BackupTableSpec(
                    "block",
                    List.of(
                            "id",
                            "block_order_number",
                            "name",
                            "description",
                            "type_id",
                            "export_file",
                            "active",
                            "wait",
                            "bot_job_id")),
            new BackupTableSpec(
                    "instruction",
                    List.of(
                            "id",
                            "instruction_order_number",
                            "actions",
                            "name",
                            "xpath",
                            "coordinates",
                            "force_coordinates",
                            "iframe_xpath",
                            "tag_name",
                            "shadow_host",
                            "shadow_root",
                            "css_selector",
                            "description",
                            "operation",
                            "optional",
                            "block_marked",
                            "default_value",
                            "action_custom_max_wait_sec",
                            "on_hold_seconds",
                            "codified",
                            "export_to_abr",
                            "active",
                            "block_id",
                            "variable_id",
                            "parent_block_id",
                            "parent_id",
                            "bot_job_id")),
            new BackupTableSpec("reference", List.of("id", "reference_type", "value", "instruction_id", "bot_job_id")),
            new BackupTableSpec(
                    "variable",
                    List.of(
                            "id",
                            "type",
                            "name",
                            "value",
                            "instruction_id",
                            "bot_job_id",
                            "local_format",
                            "delimiter")),
            // ─── component_* tables: these reference home_banking_id (not bot_job_id) ──
            new BackupTableSpec(
                    "component_block",
                    List.of(
                            "id",
                            "home_banking_id",
                            "block_order_number",
                            "name",
                            "description",
                            "type_id",
                            "export_file",
                            "active",
                            "wait")),
            new BackupTableSpec(
                    "component_instruction",
                    List.of(
                            "id",
                            "instruction_order_number",
                            "actions",
                            "name",
                            "xpath",
                            "coordinates",
                            "force_coordinates",
                            "iframe_xpath",
                            "tag_name",
                            "shadow_host",
                            "shadow_root",
                            "css_selector",
                            "description",
                            "operation",
                            "optional",
                            "block_marked",
                            "default_value",
                            "action_custom_max_wait_sec",
                            "on_hold_seconds",
                            "codified",
                            "export_to_abr",
                            "active",
                            "block_id",
                            "variable_id",
                            "parent_block_id",
                            "parent_id",
                            "home_banking_id")),
            new BackupTableSpec(
                    "component_reference",
                    List.of("id", "reference_type", "value", "instruction_id", "home_banking_id")),
            new BackupTableSpec(
                    "component_variable",
                    List.of(
                            "id",
                            "type",
                            "name",
                            "value",
                            "instruction_id",
                            "home_banking_id",
                            "local_format",
                            "delimiter")));

    /** Dialect detection for the three supported engines. */
    private enum BackupDialect {
        POSTGRES,
        SQLITE,
        ACCESS;

        static BackupDialect detect() {
            String t = com.allinweb.ch.util.ARPropertyManager.getInstance()
                    .getProperty(com.allinweb.ch.util.ARPropertyEnum.DATABASE_TYPE);
            if (t == null) return ACCESS;
            if ("Postgres".equalsIgnoreCase(t)) return POSTGRES;
            if ("TEXT".equalsIgnoreCase(t)) return SQLITE;
            return ACCESS;
        }
    }

    /**
     * Dump every row of every backed-up table into a single SQL file. FK-safe
     * insertion order. Format is byte-compatible with the legacy per-table
     * backup files (same quoting, same {@code [null]} placeholder for null
     * strings, same {@code 0/1} booleans, same {@code NULL} for null integers).
     *
     * @return {@code null} on success, {@link ErrorMessage} on failure
     */
    public ErrorMessage dumpAllToSingleFile(Connection conn, String backupFilePath) {
        File sqlFile = new File(backupFilePath);
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(sqlFile), Charset.forName("windows-1252")))) {

            String header = "-- AR-WEB SNAPSHOT v1 — "
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    + System.lineSeparator()
                    + "-- dialect (informational): " + BackupDialect.detect().name()
                    + System.lineSeparator();
            writer.write(header);
            writer.write(System.lineSeparator());

            for (BackupTableSpec spec : BACKUP_TABLES_IN_ORDER) {
                long rows = dumpOneTableToWriter(conn, writer, spec);
                log.info("dumpAllToSingleFile — {}: {} row(s)", spec.tableName, rows);
            }

            writer.flush();
            log.info("dumpAllToSingleFile — complete: {}", sqlFile.getAbsolutePath());
            return null;
        } catch (Exception error) {
            log.error("dumpAllToSingleFile — failed: {}", error.getMessage(), error);
            return new ErrorMessage("Error in backup process", "Error during single-file backup", error.getMessage());
        }
    }

    private long dumpOneTableToWriter(Connection conn, BufferedWriter writer, BackupTableSpec spec) throws Exception {
        String select = "SELECT " + String.join(", ", spec.columns) + " FROM " + spec.tableName + " ORDER BY id ASC";
        String insertPrefix = "INSERT INTO " + spec.tableName + " (" + String.join(", ", spec.columns) + ") VALUES (";

        long count = 0;
        writer.write("-- TABLE: " + spec.tableName + System.lineSeparator());
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(select)) {
            ResultSetMetaData md = rs.getMetaData();
            while (rs.next()) {
                StringBuilder line = new StringBuilder(insertPrefix);
                for (int i = 0; i < spec.columns.size(); i++) {
                    if (i > 0) line.append(", ");
                    line.append(renderValueForBackup(rs, md, i + 1));
                }
                line.append(");");
                writer.write(line.toString());
                writer.write(System.lineSeparator());
                count++;
            }
        }
        writer.write(System.lineSeparator());
        return count;
    }

    /**
     * Render one column value of the current row using the legacy format. Integer
     * columns become either the raw number or the bare {@code NULL} keyword;
     * anything else is treated as a string — quoted, single-quotes escaped as
     * {@code ''}, and null/blank values written as the literal {@code '[null]'}
     * exactly like {@link #toSqlValue(String)} does.
     */
    private String renderValueForBackup(ResultSet rs, ResultSetMetaData md, int index) throws SQLException {
        int type = md.getColumnType(index);
        switch (type) {
            case Types.INTEGER:
            case Types.SMALLINT:
            case Types.TINYINT:
            case Types.BIGINT:
            case Types.BIT:
            case Types.BOOLEAN: {
                long v = rs.getLong(index);
                if (rs.wasNull()) return "NULL";
                // BIT / BOOLEAN normalise to 0/1 (Postgres BOOLEAN columns map here too).
                if (type == Types.BIT || type == Types.BOOLEAN) return v == 0 ? "0" : "1";
                return Long.toString(v);
            }
            default: {
                String s = rs.getString(index);
                if (s == null || s.isBlank()) return "'[null]'";
                return "'" + s.replace("'", "''") + "'";
            }
        }
    }

    /**
     * Single-file restore that works uniformly across Access, SQLite and
     * Postgres. Every backed-up table is wiped, the per-dialect identity
     * counter is reset to 1, then the per-table restore methods replay the
     * INSERTs without their original ids — the database assigns fresh ids
     * starting at 1 on each table, and FK columns on children are rewritten
     * using the old&nbsp;→&nbsp;new id maps ({@link #homeBankMap},
     * {@link #botJobMap}, {@link #blockMap}, etc.) captured as each parent
     * table is loaded.
     *
     * <p>Counter reset is dialect-specific:
     * <ul>
     *   <li><b>SQLite</b>: {@code DELETE FROM sqlite_sequence} for each
     *       backed-up table so the first INSERT seeds the counter at 1.</li>
     *   <li><b>Postgres</b>: {@code setval(pg_get_serial_sequence('t','id'), 1, false)}
     *       so {@code nextval()} returns 1 on the first INSERT.</li>
     *   <li><b>Access</b>: no-op — ucanaccess's COUNTER naturally restarts
     *       after the per-table wipe that {@link #restoreHomeBanking} performs
     *       at the head of the chain.</li>
     * </ul>
     *
     * <p>The calling sequence mirrors
     * {@code ARConfigurationPane.runLegacyPerTableRestore} exactly — including
     * the two map-only "update" passes that patch
     * {@code instruction.parent_id} / {@code instruction.variable_id} and the
     * {@code component_instruction} equivalents after their targets exist.
     *
     * @param conn an open JDBC connection to the current database
     * @param sqlFilePath path to the single-file {@code .sql} dump
     * @return {@code null} on success, {@link ErrorMessage} on any failure
     */
    public ErrorMessage restoreWithRemap(Connection conn, String sqlFilePath) {
        File sqlFile = new File(sqlFilePath);
        if (!sqlFile.exists()) {
            return new ErrorMessage(
                    "Restore file not found", "Single-file backup not present on disk", sqlFile.getAbsolutePath());
        }

        Path tempDir = null;
        try {
            BackupDialect dialect = BackupDialect.detect();
            resetIdentityCountersToOne(conn, dialect);

            tempDir = Files.createTempDirectory("ar-restore-");
            Map<String, Path> perTableFiles = splitSingleFileByTable(sqlFile, tempDir);

            // Chain the legacy per-table restore methods in the same FK-safe
            // order runLegacyPerTableRestore uses. Every method populates its
            // remap TreeMap (homeBankMap, botJobMap, ...) which the later calls
            // consult when rewriting FK columns on their own rows.
            ErrorMessage error = restoreHomeBanking(conn, pathOrEmpty(perTableFiles, "home_banking"));
            if (error != null) return error;

            error = restoreHomeUrl(conn, pathOrEmpty(perTableFiles, "home_url"));
            if (error != null) return error;

            error = restoreBotJob(conn, pathOrEmpty(perTableFiles, "bot_job"), null, null, null);
            if (error != null) return error;

            error = restoreBlock(conn, pathOrEmpty(perTableFiles, "block"), null);
            if (error != null) return error;

            error = restoreInstruction(conn, pathOrEmpty(perTableFiles, "instruction"), null);
            if (error != null) return error;

            error = restoreVariable(conn, pathOrEmpty(perTableFiles, "variable"), null);
            if (error != null) return error;

            // Map-only pass: rewrites instruction.parent_id / variable_id to the
            // new ids captured by the two calls above. No file input.
            error = restoreUpdateInstruction(conn, null);
            if (error != null) return error;

            error = restoreReference(conn, pathOrEmpty(perTableFiles, "reference"), null);
            if (error != null) return error;

            error = restoreComponentBlock(conn, pathOrEmpty(perTableFiles, "component_block"));
            if (error != null) return error;

            error = restoreComponentInstruction(conn, pathOrEmpty(perTableFiles, "component_instruction"));
            if (error != null) return error;

            error = restoreComponentVariable(conn, pathOrEmpty(perTableFiles, "component_variable"));
            if (error != null) return error;

            error = restoreComponentUpdateInstruction(conn);
            if (error != null) return error;

            error = restoreComponentReference(conn, pathOrEmpty(perTableFiles, "component_reference"));
            if (error != null) return error;

            log.info("restoreWithRemap — complete: {}", sqlFile.getAbsolutePath());
            return null;
        } catch (Exception error) {
            log.error("restoreWithRemap — failed: {}", error.getMessage(), error);
            return new ErrorMessage("Error in restore process", "Error during single-file restore", error.getMessage());
        } finally {
            if (tempDir != null) {
                deleteRecursively(tempDir);
            }
        }
    }

    /**
     * Reset the identity counter for every backed-up table so the next INSERT
     * (done with no explicit id by the per-table restore methods) starts at 1.
     *
     * <p>For SQLite this is {@code DELETE FROM sqlite_sequence WHERE name = ?};
     * the counter row is re-created by SQLite the first time a row is inserted
     * into an AUTOINCREMENT column, with seed 1. For Postgres we call
     * {@code setval(pg_get_serial_sequence(...), 1, false)} so the next
     * {@code nextval()} returns 1. For Access we do nothing — ucanaccess's
     * COUNTER column naturally restarts at 1 after the table is emptied by
     * {@link #restoreHomeBanking}'s opening {@code DELETE FROM} cascade.
     */
    private void resetIdentityCountersToOne(Connection conn, BackupDialect dialect) {
        if (dialect == BackupDialect.ACCESS) {
            return;
        }
        if (dialect == BackupDialect.SQLITE) {
            try (Statement st = conn.createStatement()) {
                for (BackupTableSpec spec : BACKUP_TABLES_IN_ORDER) {
                    try {
                        int n = st.executeUpdate("DELETE FROM sqlite_sequence WHERE name = '" + spec.tableName + "'");
                        log.info("resetIdentityCountersToOne — SQLite {} ({} sequence row cleared)", spec.tableName, n);
                    } catch (SQLException seqEx) {
                        // sqlite_sequence only exists once a row has been inserted into
                        // any AUTOINCREMENT column. On a pristine DB it's missing — that
                        // is already "next id will be 1", so the miss is harmless.
                        log.warn(
                                "resetIdentityCountersToOne — SQLite reset skipped for {}: {}",
                                spec.tableName,
                                seqEx.getMessage());
                        return;
                    }
                }
            } catch (SQLException e) {
                log.warn("resetIdentityCountersToOne — SQLite statement open failed: {}", e.getMessage());
            }
            return;
        }
        if (dialect == BackupDialect.POSTGRES) {
            for (BackupTableSpec spec : BACKUP_TABLES_IN_ORDER) {
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate("SELECT setval(pg_get_serial_sequence('" + spec.tableName + "', 'id'), 1, false)");
                    log.info("resetIdentityCountersToOne — Postgres {} seq -> 1", spec.tableName);
                } catch (SQLException e) {
                    // Table with no declared sequence shouldn't exist in this list,
                    // but tolerate it so one missing sequence doesn't block the rest.
                    log.warn(
                            "resetIdentityCountersToOne — Postgres reset skipped for {}: {}",
                            spec.tableName,
                            e.getMessage());
                }
            }
        }
    }

    /**
     * Walk the single-file dump and bucket every INSERT into its table's own
     * file using the {@code -- TABLE: <name>} markers written by
     * {@link #dumpOneTableToWriter}. Comment lines ({@code --}) are dropped —
     * the legacy per-table restore parsers don't skip them.
     */
    private Map<String, Path> splitSingleFileByTable(File sqlFile, Path tempDir) throws IOException {
        Map<String, Path> perTable = new java.util.LinkedHashMap<>();
        Map<String, BufferedWriter> writers = new java.util.LinkedHashMap<>();
        String currentTable = null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(sqlFile), Charset.forName("windows-1252")))) {
            String raw;
            while ((raw = reader.readLine()) != null) {
                String line = raw;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if (trimmed.startsWith("-- TABLE:")) {
                    currentTable = trimmed.substring("-- TABLE:".length()).trim();
                    perTable.computeIfAbsent(currentTable, t -> tempDir.resolve("backup_" + t + "_all.sql"));
                    writers.computeIfAbsent(currentTable, t -> {
                        try {
                            return new BufferedWriter(new OutputStreamWriter(
                                    new FileOutputStream(perTable.get(t).toFile()), Charset.forName("windows-1252")));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                    continue;
                }
                if (trimmed.startsWith("--")) continue;
                if (currentTable == null) continue;
                BufferedWriter w = writers.get(currentTable);
                w.write(line);
                w.write(System.lineSeparator());
            }
        } finally {
            for (BufferedWriter w : writers.values()) {
                try {
                    w.flush();
                    w.close();
                } catch (IOException ignore) {
                    // best-effort close
                }
            }
        }
        return perTable;
    }

    private String pathOrEmpty(Map<String, Path> perTable, String table) {
        Path p = perTable.get(table);
        return p == null ? "" : p.toAbsolutePath().toString();
    }

    private void deleteRecursively(Path root) {
        try {
            if (!Files.exists(root)) return;
            try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignore) {
                        // best-effort cleanup
                    }
                });
            }
        } catch (IOException e) {
            log.warn("deleteRecursively — failed to clean temp dir {}: {}", root, e.getMessage());
        }
    }

    /**
     * Single-file export of one bot job + every row it transitively depends on.
     * Writes a file in the same {@code -- TABLE: <name>} section format used by
     * {@link #dumpAllToSingleFile}, but restricted to the six tables touched by
     * a bot-job scoped backup: {@code home_banking}, {@code bot_job},
     * {@code block}, {@code instruction}, {@code variable}, {@code reference}.
     *
     * <p>Under the hood the existing per-table backup methods
     * ({@link #backupHomeBanking}, {@link #backupBotJob}, …) are invoked into
     * temp files (preserving their exact WHERE filters and formatting) and
     * then concatenated with section markers. This keeps row-byte output
     * identical to the legacy 6-file format for a given table.
     */
    public ErrorMessage dumpBotJobToSingleFile(
            Connection conn, String backupFilePath, int homeBankingId, int botJobId) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("ar-export-botjob-");
            Path hb = tempDir.resolve("home_banking.sql");
            Path bj = tempDir.resolve("bot_job.sql");
            Path bl = tempDir.resolve("block.sql");
            Path in = tempDir.resolve("instruction.sql");
            Path vr = tempDir.resolve("variable.sql");
            Path rf = tempDir.resolve("reference.sql");

            ErrorMessage err = backupHomeBanking(conn, hb.toString(), homeBankingId);
            if (err != null) return err;
            err = backupBotJob(conn, bj.toString(), homeBankingId, botJobId);
            if (err != null) return err;
            err = backupBlock(conn, bl.toString(), botJobId);
            if (err != null) return err;
            err = backupInstruction(conn, in.toString(), botJobId);
            if (err != null) return err;
            err = backupVariable(conn, vr.toString(), botJobId);
            if (err != null) return err;
            err = backupReference(conn, rf.toString(), botJobId);
            if (err != null) return err;

            Map<String, Path> sections = new java.util.LinkedHashMap<>();
            sections.put("home_banking", hb);
            sections.put("bot_job", bj);
            sections.put("block", bl);
            sections.put("instruction", in);
            sections.put("variable", vr);
            sections.put("reference", rf);

            File outFile = new File(backupFilePath);
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(outFile), Charset.forName("windows-1252")))) {
                writer.write("-- AR-WEB BOT JOB SNAPSHOT v1 — "
                        + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                writer.write(System.lineSeparator());
                writer.write(
                        "-- dialect (informational): " + BackupDialect.detect().name());
                writer.write(System.lineSeparator());
                writer.write("-- home_banking_id: " + homeBankingId + ", bot_job_id: " + botJobId);
                writer.write(System.lineSeparator());
                writer.write(System.lineSeparator());

                for (Map.Entry<String, Path> e : sections.entrySet()) {
                    writer.write("-- TABLE: " + e.getKey());
                    writer.write(System.lineSeparator());
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(
                            new FileInputStream(e.getValue().toFile()), Charset.forName("windows-1252")))) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            writer.write(line);
                            writer.write(System.lineSeparator());
                        }
                    }
                    writer.write(System.lineSeparator());
                }
            }
            log.info("dumpBotJobToSingleFile — complete: {}", outFile.getAbsolutePath());
            return null;
        } catch (Exception error) {
            log.error("dumpBotJobToSingleFile — failed: {}", error.getMessage(), error);
            return new ErrorMessage(
                    "Error in export process", "Error during bot job single-file export", error.getMessage());
        } finally {
            if (tempDir != null) {
                deleteRecursively(tempDir);
            }
        }
    }

    /**
     * Single-file import that restores one bot job into the currently-open DB,
     * re-keying every FK so the job folds into the existing {@code home_banking}
     * (and its {@code home_url}) row whose ids are passed in. The file is
     * split into the six per-table sections produced by
     * {@link #dumpBotJobToSingleFile} and each section is handed to its legacy
     * restore method — which strips old ids, lets the engine assign fresh
     * ones, and remaps child FK columns via {@link #botJobMap} /
     * {@link #blockMap} / {@link #instructionMap} / {@link #variableMap}.
     *
     * <p>The {@code home_banking} section is NOT re-inserted — the caller's
     * current home_banking row is reused. That section is only read to check
     * the exported org name matches the target org (prevents cross-tenant
     * imports), same as the legacy 6-file flow did.
     */
    public ErrorMessage restoreBotJobFromSingleFile(
            Connection conn,
            String sqlFilePath,
            Integer homeBankIdImported,
            Integer homeUrlIdImported,
            Integer botJobIdImported,
            String organizationName) {
        File sqlFile = new File(sqlFilePath);
        if (!sqlFile.exists()) {
            return new ErrorMessage(
                    "Import Failed: File Not Found",
                    "Import attempt failed: " + sqlFilePath,
                    "The file was not found. Please execute the Export Bot Job first or select the correct directory.");
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("ar-import-botjob-");
            Map<String, Path> perTableFiles = splitSingleFileByTable(sqlFile, tempDir);

            ErrorMessage error =
                    getHomeBankingNameFromFile(pathOrEmpty(perTableFiles, "home_banking"), organizationName);
            if (error != null) return error;

            error = restoreBotJob(
                    conn,
                    pathOrEmpty(perTableFiles, "bot_job"),
                    homeBankIdImported,
                    homeUrlIdImported,
                    botJobIdImported);
            if (error != null) return error;

            error = restoreBlock(conn, pathOrEmpty(perTableFiles, "block"), botJobIdImported);
            if (error != null) return error;

            error = restoreInstruction(conn, pathOrEmpty(perTableFiles, "instruction"), botJobIdImported);
            if (error != null) return error;

            error = restoreVariable(conn, pathOrEmpty(perTableFiles, "variable"), botJobIdImported);
            if (error != null) return error;

            error = restoreUpdateInstruction(conn, botJobIdImported);
            if (error != null) return error;

            error = restoreReference(conn, pathOrEmpty(perTableFiles, "reference"), botJobIdImported);
            if (error != null) return error;

            log.info("restoreBotJobFromSingleFile — complete: {}", sqlFile.getAbsolutePath());
            return null;
        } catch (Exception error) {
            log.error("restoreBotJobFromSingleFile — failed: {}", error.getMessage(), error);
            return new ErrorMessage(
                    "Error in import process", "Error during bot job single-file import", error.getMessage());
        } finally {
            if (tempDir != null) {
                deleteRecursively(tempDir);
            }
        }
    }

    /**
     * At backup time, drop a timestamped binary copy of the current DB file
     * into {@code destFolder} alongside the SQL dump. No-op for Postgres
     * (server-managed storage) and any other non-file engine.
     *
     * <p>Naming:
     * <ul>
     *   <li>Access &rarr; {@code access_backup_YYYYMMDD_HHMMSS.mdb}</li>
     *   <li>SQLite (TEXT) &rarr; {@code sqlite_backup_YYYYMMDD_HHMMSS.db}</li>
     * </ul>
     */
    public ErrorMessage copyDbFileTo(String dataBaseType, String dbFolder, String destFolder) {
        if (dataBaseType == null) return null;
        String type = dataBaseType.trim();
        boolean isAccess = "Access".equalsIgnoreCase(type);
        boolean isSqlite = "TEXT".equalsIgnoreCase(type);
        if (!isAccess && !isSqlite) {
            log.info("copyDbFileTo — {} is not a file-backed DB, skipping", type);
            return null;
        }
        if (dbFolder == null || dbFolder.isBlank() || destFolder == null || destFolder.isBlank()) {
            return new ErrorMessage(
                    "Backup copy failed",
                    "Database folder or destination folder is empty",
                    "dbFolder='" + dbFolder + "', destFolder='" + destFolder + "'");
        }

        String fileName = isAccess ? ARConstants.FILE_NAME_ACCESS : ARConstants.FILE_NAME_SQLITE;
        File dbFile = new File(dbFolder + fileName);
        if (!dbFile.exists()) {
            log.info("copyDbFileTo — {} not found, nothing to copy", dbFile.getAbsolutePath());
            return null;
        }

        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String copyName = isAccess ? "/access_backup_" + ts + ".mdb" : "/sqlite_backup_" + ts + ".db";
        File destFile = new File(destFolder + copyName);

        try {
            Files.copy(dbFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log.info("copyDbFileTo — copied {} -> {}", dbFile.getAbsolutePath(), destFile.getAbsolutePath());
            return null;
        } catch (IOException e) {
            log.error("copyDbFileTo — copy failed: {}", e.getMessage(), e);
            return new ErrorMessage(
                    "Backup copy failed", "Could not copy " + dbFile.getName() + " to backup folder", e.getMessage());
        }
    }
}
