package com.allinweb.ch.facade;

import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import java.io.*;
import java.nio.charset.Charset;
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

                    String insert = String.format(
                            "INSERT INTO instruction (id, instruction_order_number, actions, name, xpath, coordinates, force_coordinates, iframe_xpath, tag_name, shadow_host, shadow_root, css_selector, description, operation, optional, block_marked, default_value, action_custom_max_wait_sec, on_hold_seconds, codified, export_to_abr, active, block_id, variable_id, parent_block_id, parent_id, bot_job_id) "
                                    + "VALUES (%d, %d, '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', %d, %d, '%s', %s, %s, %d, %d, %d, %s, %s, %s, %s, %s);",
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
                            botJobIdWasNull ? "NULL" : botJobId);

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
                            export_to_abr, active, block_id, variable_id, parent_block_id, parent_id, bot_job_id
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
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

                    if (values.size() != 26 && values.size() != 27) {
                        return new ErrorMessage(
                                "Parse Error",
                                "Expected 26 or 27 values for instruction, but got " + values.size(),
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
     * Restore a single-file backup produced by {@link #dumpAllToSingleFile}. The
     * current database is wiped (all rows in every backed-up table deleted, in
     * reverse FK-dependency order) before the INSERTs run. ID columns are reused
     * verbatim from the file so every FK reference remains valid.
     *
     * <p>Per-dialect handling:
     * <ul>
     *   <li><b>SQLite</b>: {@code PRAGMA foreign_keys = OFF} for the duration of
     *       the restore, then {@code ON} at the end.</li>
     *   <li><b>Postgres</b>: {@code SET session_replication_role = 'replica'} to
     *       suppress FK checks, then {@code 'origin'} at the end. Sequences for
     *       every restored table are re-synced with {@code setval(pg_get_serial_sequence(...))}
     *       so future auto-increments don't collide with restored ids.</li>
     *   <li><b>Access</b>: nothing special — ucanaccess respects the insertion
     *       order and the declared schema has no self-referential FKs.</li>
     * </ul>
     */
    public ErrorMessage restoreAllFromSingleFile(Connection conn, String sqlFilePath) {
        File sqlFile = new File(sqlFilePath);
        if (!sqlFile.exists()) {
            return new ErrorMessage(
                    "Restore file not found", "Single-file backup not present on disk", sqlFile.getAbsolutePath());
        }
        BackupDialect dialect = BackupDialect.detect();
        boolean originalAutoCommit = true;
        try {
            originalAutoCommit = conn.getAutoCommit();
        } catch (SQLException ignore) {
            // some drivers forbid reading autoCommit on a closed tx; default is true
        }

        try {
            conn.setAutoCommit(false);
            setFkEnforcement(conn, dialect, false);

            // 1. Wipe every backed-up table in reverse insertion order (child tables first).
            for (int i = BACKUP_TABLES_IN_ORDER.size() - 1; i >= 0; i--) {
                BackupTableSpec spec = BACKUP_TABLES_IN_ORDER.get(i);
                try (Statement st = conn.createStatement()) {
                    int n = st.executeUpdate("DELETE FROM " + spec.tableName);
                    log.info("restoreAllFromSingleFile — wiped {}: {} row(s)", spec.tableName, n);
                }
            }

            // 2. Replay every INSERT from the file. Comments / blanks are skipped.
            long executed = runSqlScript(conn, sqlFile);
            log.info("restoreAllFromSingleFile — executed {} statement(s)", executed);

            // 3. Postgres only: resync sequences so new inserts don't collide.
            if (dialect == BackupDialect.POSTGRES) {
                for (BackupTableSpec spec : BACKUP_TABLES_IN_ORDER) {
                    try (Statement st = conn.createStatement()) {
                        st.executeUpdate("SELECT setval(pg_get_serial_sequence('" + spec.tableName + "', 'id'), "
                                + "COALESCE((SELECT MAX(id) FROM " + spec.tableName + "), 1))");
                    } catch (SQLException e) {
                        // If a table has no sequence (shouldn't happen here), just log and continue.
                        log.warn(
                                "restoreAllFromSingleFile — sequence resync skipped for {}: {}",
                                spec.tableName,
                                e.getMessage());
                    }
                }
            }

            setFkEnforcement(conn, dialect, true);
            conn.commit();
            log.info("restoreAllFromSingleFile — complete: {}", sqlFile.getAbsolutePath());
            return null;
        } catch (Exception error) {
            log.error("restoreAllFromSingleFile — failed: {}", error.getMessage(), error);
            try {
                conn.rollback();
            } catch (SQLException rbEx) {
                log.warn("restoreAllFromSingleFile — rollback failed: {}", rbEx.getMessage());
            }
            // Best-effort re-enable of FK enforcement so the connection isn't left in an odd state.
            try {
                setFkEnforcement(conn, dialect, true);
            } catch (Exception ignore) {
                // already logged upstream
            }
            return new ErrorMessage("Error in restore process", "Error during single-file restore", error.getMessage());
        } finally {
            try {
                conn.setAutoCommit(originalAutoCommit);
            } catch (SQLException ignore) {
                // noop
            }
        }
    }

    /** Toggle FK enforcement for the current connection in a dialect-aware way. */
    private void setFkEnforcement(Connection conn, BackupDialect dialect, boolean enabled) throws SQLException {
        switch (dialect) {
            case SQLITE:
                try (Statement st = conn.createStatement()) {
                    st.execute("PRAGMA foreign_keys = " + (enabled ? "ON" : "OFF"));
                }
                break;
            case POSTGRES:
                try (Statement st = conn.createStatement()) {
                    // session_replication_role = 'replica' disables all FK triggers for this session
                    st.execute("SET session_replication_role = '" + (enabled ? "origin" : "replica") + "'");
                }
                break;
            case ACCESS:
            default:
                // ucanaccess doesn't expose a session-level FK toggle, and the insertion
                // order already satisfies every declared FK — no action needed.
                break;
        }
    }

    /**
     * Quote-aware SQL script runner. Each statement is terminated by a {@code ';'}
     * appearing <em>outside</em> any single-quoted string literal; the {@code ';'}
     * may land on the same line or on any subsequent line, so multi-line string
     * values (which appear in the legacy backup files — e.g. {@code search_config}
     * on {@code home_banking} rows) are reassembled correctly into one statement.
     *
     * <p>Handling:
     * <ul>
     *   <li>Lines starting with {@code --} are treated as comments and skipped
     *       ONLY when we are not inside a quoted string. Inside a string, the
     *       {@code '--'} is real data and must be preserved.</li>
     *   <li>Single quotes toggle the quote state. The standard SQL {@code ''}
     *       escape — a doubled single quote inside a quoted literal — is consumed
     *       as two characters without flipping the state.</li>
     *   <li>A trailing {@code ';'} is stripped before execution, because some
     *       JDBC drivers object to it while most tolerate it.</li>
     *   <li>If the file ends mid-statement (no final {@code ';'}), whatever was
     *       accumulated is executed as a best-effort last statement.</li>
     * </ul>
     */
    private long runSqlScript(Connection conn, File sqlFile) throws IOException, SQLException {
        long executed = 0;
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;

        try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(sqlFile), Charset.forName("windows-1252")));
                Statement st = conn.createStatement()) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Comment / blank line handling — only applies OUTSIDE a quoted literal.
                if (!inQuote) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) continue;
                    if (trimmed.startsWith("--")) continue;
                }

                int i = 0;
                int n = line.length();
                while (i < n) {
                    char c = line.charAt(i);
                    if (c == '\'') {
                        if (inQuote && i + 1 < n && line.charAt(i + 1) == '\'') {
                            // '' escape inside a quoted literal — keep both, stay in quote
                            current.append('\'').append('\'');
                            i += 2;
                            continue;
                        }
                        inQuote = !inQuote;
                        current.append('\'');
                        i++;
                    } else if (c == ';' && !inQuote) {
                        String stmt = current.toString().trim();
                        current.setLength(0);
                        if (!stmt.isEmpty()) {
                            try {
                                st.executeUpdate(stmt);
                                executed++;
                            } catch (SQLException e) {
                                log.error(
                                        "runSqlScript — statement #{} failed: {} | stmt head: {}",
                                        executed + 1,
                                        e.getMessage(),
                                        stmt.length() > 300 ? stmt.substring(0, 300) + "…" : stmt);
                                throw e;
                            }
                        }
                        i++;
                    } else {
                        current.append(c);
                        i++;
                    }
                }
                // Preserve the original newline when the line ended inside a string —
                // multi-line values round-trip cleanly.
                if (inQuote) {
                    current.append('\n');
                }
            }

            // File ended; if there's a trailing statement without ';', try it.
            String tail = current.toString().trim();
            if (!tail.isEmpty()) {
                st.executeUpdate(tail);
                executed++;
            }
        }
        return executed;
    }
}
