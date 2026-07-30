package com.allinweb.ch.facade;

import com.allinweb.ch.component.pane.BotJobDetailsWorkspaceHost;
import com.allinweb.ch.driver.ARPlaywrightDriver;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.actions.RuntimeVariableMemoryRegistry;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConfigService {

    private static final ARPropertyManager arPropertyManager = ARPropertyManager.getInstance();
    private static final PerformLists performLists = PerformLists.getInstance();
    private static final PerformDBEngine performDBEngine = PerformDBEngine.getInstance();
    private static final PerformDataBase performDataBase = PerformDataBase.getInstance();
    private static final PerformBackup performBackup = PerformBackup.getInstance();
    private static final PerformInitializer performInitializer = PerformInitializer.getInstance();
    private static final MainDashboardService mainDashboardService = MainDashboardService.getInstance();
    private static final WebSocketSessionManager webSocketSessionManager = WebSocketSessionManager.getInstance();
    private static final Gson gson = new Gson();
    private static final ConfigSceneShutdownService sceneShutdownService =
            new ConfigSceneShutdownService(ConfigSceneShutdownRegistry.getInstance()::current);
    private static final Map<String, String> PATH_FIELD_MODES = Map.ofEntries(
            Map.entry("pathLicense", "directory"),
            Map.entry("pathExcel", "directory"),
            Map.entry("pathLog", "directory"),
            Map.entry("pathDb", "directory"),
            Map.entry("pathReport", "directory"),
            Map.entry("pathPriority", "directory"),
            Map.entry("pathEngine", "file"),
            Map.entry("pathWebDriver", "directory"),
            Map.entry("pathAppium", "directory"),
            Map.entry("pathPlugins", "directory"));

    protected static volatile ConfigService instance;

    private ConfigService() {}

    public static ConfigService getInstance() {
        if (instance == null) {
            synchronized (ConfigService.class) {
                if (instance == null) {
                    instance = new ConfigService();
                }
            }
        }
        return instance;
    }

    public Map<String, Object> bootstrap() {
        reloadLists();
        return configResponse("Configuration loaded");
    }

    public Map<String, Object> choosePath(JsonObject body) {
        String field = str(body, "field");
        String expectedMode = PATH_FIELD_MODES.get(field);
        String mode = str(body, "mode").toLowerCase(java.util.Locale.ROOT);
        if (expectedMode == null || !expectedMode.equals(mode)) {
            return correlatePathResponse(
                    body, failure("Unsupported configuration path selection"));
        }
        String currentPath = str(body, "currentPath");
        if (currentPath.isEmpty()) {
            Object configuredPath = currentConfig().get(field);
            currentPath = configuredPath == null ? "" : configuredPath.toString();
        }
        final String path;
        try {
            path = presentation().choosePath(mode, currentPath);
        } catch (RuntimeException chooserFailure) {
            log.warn("Config {} chooser failed: {}", field, chooserFailure.getMessage());
            return correlatePathResponse(
                    body, failure("Unable to open the native path selector"));
        }
        Map<String, Object> response = ok(path == null ? "Path selection cancelled" : "Path selected");
        response.put("field", field);
        response.put("path", path);
        response.put("cancelled", path == null);
        return correlatePathResponse(body, response);
    }

    public Map<String, Object> save(JsonObject body) {
        JsonObject config = body != null && body.has("config") && body.get("config").isJsonObject()
                ? body.get("config").getAsJsonObject()
                : body;
        Map<String, Object> validation = validate(config);
        if (Boolean.FALSE.equals(validation.get("ok"))) {
            return validation;
        }

        String requestedBrowser = supportedBrowser(str(config, "browser"));
        if (requestedBrowser.isEmpty()) {
            return failure("Select Chrome, Edge, or Firefox");
        }
        String activeBrowser = activePlaywrightBrowser();
        if (!activeBrowser.isEmpty() && !activeBrowser.equalsIgnoreCase(requestedBrowser)) {
            String message = "unknown".equals(activeBrowser)
                    ? "An existing Playwright browser is active. Use the Browser dropdown and confirm replacement with "
                            + requestedBrowser
                            + " before saving the full configuration."
                    : "The active Playwright browser is "
                            + activeBrowser
                            + ". Use the Browser dropdown and confirm replacement with "
                            + requestedBrowser
                            + " before saving the full configuration.";
            Map<String, Object> response = failure(message);
            response.put("browserUpdateRequired", true);
            response.put("activeBrowser", activeBrowser);
            response.put("requestedBrowser", requestedBrowser);
            return response;
        }

        String databaseType = str(config, "databaseType");
        String pathDb = str(config, "pathDb");
        String dbUrl = str(config, "dbUrl");
        String dbUser = str(config, "dbUser");
        String dbPwd = str(config, "dbPwd");

        try {
            performInitializer.testConnection(databaseType, pathDb, dbUrl, dbUser, dbPwd);
        } catch (Exception error) {
            return failure("Database connection Failed: " + error.getMessage());
        }

        Map<String, String> updates = new LinkedHashMap<>();
        put(updates, ARPropertyEnum.BROWSER, requestedBrowser);
        put(updates, ARPropertyEnum.PATH_LICENSE, str(config, "pathLicense"));
        put(updates, ARPropertyEnum.PATH_EXCEL, str(config, "pathExcel"));
        put(updates, ARPropertyEnum.PATH_LOG, str(config, "pathLog"));
        put(updates, ARPropertyEnum.PATH_PRIORITY, str(config, "pathPriority"));
        put(updates, ARPropertyEnum.PATH_REPORT, str(config, "pathReport"));
        put(updates, ARPropertyEnum.PATH_ENGINE, str(config, "pathEngine"));
        put(updates, ARPropertyEnum.PATH_WEBDRIVER, str(config, "pathWebDriver"));
        put(updates, ARPropertyEnum.PATH_APPIUM, str(config, "pathAppium"));
        put(updates, ARPropertyEnum.PATH_PLUGINS, str(config, "pathPlugins"));
        put(updates, ARPropertyEnum.URL_PLUGINS, str(config, "urlPlugins"));
        put(updates, ARPropertyEnum.AI_API_KEY, str(config, "aiApiKey"));
        put(updates, ARPropertyEnum.AI_ENDPOINT, str(config, "aiEndpoint"));
        put(updates, ARPropertyEnum.AI_MODEL, str(config, "aiModel"));
        put(updates, ARPropertyEnum.AI_MAX_BLOCKS, str(config, "aiMaxBlocks"));
        put(updates, ARPropertyEnum.DATABASE_TYPE, databaseType);
        put(updates, ARPropertyEnum.PATH_DB, pathDb);
        put(updates, ARPropertyEnum.DB_URL, dbUrl);
        put(updates, ARPropertyEnum.DB_USER, dbUser);
        put(updates, ARPropertyEnum.DB_PWD, dbPwd);
        try {
            arPropertyManager.setPropertiesChecked(updates);
        } catch (IOException persistenceFailure) {
            log.error(
                    "Could not persist configuration to {}",
                    arPropertyManager.getConfigurationFileName(),
                    persistenceFailure);
            return failure("Configuration could not be written to the active config file");
        }

        try {
            performDataBase.changeDbConnection();
        } catch (SQLException error) {
            return failure("Database change failed: " + error.getMessage());
        }
        RuntimeVariableMemoryRegistry.getInstance().clearAll();

        performLists.clearAllLists();
        ErrorMessage error = reloadLists();
        if (error != null) {
            return failure("Configuration saved but reload failed", error);
        }
        closeAllScenes();
        pushMainDashboard();
        return configResponse("Configuration saved");
    }

    public Map<String, Object> backup(JsonObject body) {
        String selectedDb = str(body, "databaseType");
        String savedDb = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);
        if (!dbMatches(savedDb, selectedDb)) {
            return failure("The selected database type does not match the saved database");
        }
        String folder = str(body, "destinationFolder");
        if (Strings.isNullOrEmpty(folder)) {
            String initialPath = str(body, "initialPath");
            if (Strings.isNullOrEmpty(initialPath)) {
                initialPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
            }
            try {
                folder = presentation().choosePath("directory", initialPath);
            } catch (RuntimeException chooserFailure) {
                log.warn("Database backup folder chooser failed: {}", chooserFailure.getMessage());
                return failure("Unable to open the backup destination folder selector");
            }
        }
        if (Strings.isNullOrEmpty(folder)) {
            Map<String, Object> response = ok("Database backup cancelled");
            response.put("cancelled", true);
            return response;
        }
        try {
            performDataBase.changeDbConnection();
        } catch (SQLException error) {
            return failure("Database connection failed: " + error.getMessage());
        }

        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"));
        String backupFileName = "backup_" + dbDialectSlug(savedDb) + "_all_" + date + ".sql";
        String backupFilePath = folder + File.separator + backupFileName;
        ErrorMessage error;
        try (Connection conn = performDataBase.getConnection()) {
            performBackup.initialize(conn);
            error = performBackup.dumpAllToSingleFile(conn, backupFilePath);
            if (error == null) {
                error = performBackup.copyDbFileTo(savedDb, arPropertyManager.getProperty(ARPropertyEnum.PATH_DB), folder);
            }
        } catch (SQLException ex) {
            return failure("Backup failed: " + ex.getMessage());
        }
        if (error != null) {
            return failure("Backup failed", error);
        }
        Map<String, Object> response = ok("Database backup completed");
        response.put("cancelled", false);
        response.put("folder", folder);
        response.put("fileName", backupFileName);
        response.put("path", backupFilePath);
        return response;
    }

    public Map<String, Object> restore(JsonObject body) {
        String selectedDb = str(body, "databaseType");
        String savedDb = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);
        if (!dbMatches(savedDb, selectedDb)) {
            return failure("The selected database type does not match the saved database");
        }
        String date = str(body, "date");
        if (Strings.isNullOrEmpty(date)) {
            return failure("Please select a date to restore from");
        }
        String folder = str(body, "sourceFolder");
        if (Strings.isNullOrEmpty(folder)) {
            String initialPath = str(body, "initialPath");
            if (Strings.isNullOrEmpty(initialPath)) {
                initialPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
            }
            try {
                folder = presentation().choosePath("directory", initialPath);
            } catch (RuntimeException chooserFailure) {
                log.warn("Database restore folder chooser failed: {}", chooserFailure.getMessage());
                return failure("Unable to open the restore source folder selector");
            }
        }
        if (Strings.isNullOrEmpty(folder)) {
            return failure("Restore cancelled");
        }
        File single = new File(folder, "backup_" + dbDialectSlug(savedDb) + "_all_" + date + ".sql");
        File plainSingle = new File(folder, "backup_all_" + date + ".sql");
        File legacyMarker = new File(folder, "backup_home_banking_" + date + ".sql");
        boolean useSingle = single.exists() || plainSingle.exists();
        File restoreFile = single.exists() ? single : plainSingle;
        if (!useSingle && !legacyMarker.exists()) {
            return failure("No backup for that date was found in the selected folder");
        }

        ErrorMessage error;
        try (Connection conn = performDataBase.getConnection()) {
            performBackup.initialize(conn);
            if (useSingle) {
                error = performBackup.restoreWithRemap(conn, restoreFile.getAbsolutePath());
            } else {
                error = runLegacyPerTableRestore(conn, folder, date);
            }
        } catch (SQLException ex) {
            return failure("Restore failed: " + ex.getMessage());
        }
        if (error != null) {
            return failure("Restore failed", error);
        }
        RuntimeVariableMemoryRegistry.getInstance().clearAll();
        performLists.clearAllLists();
        error = reloadLists();
        if (error != null) {
            return failure("Database restored but reload failed", error);
        }
        closeAllScenes();
        pushMainDashboard();
        return configResponse("Database restored");
    }

    /**
     * Persists only the global browser property. Replacing a live, different Playwright browser is
     * explicit because the application owns exactly one shared browser session.
     */
    public Map<String, Object> updateBrowser(JsonObject body) {
        String requestedBrowser = supportedBrowser(str(body, "browser"));
        if (requestedBrowser.isEmpty()) {
            return failure("Select Chrome, Edge, or Firefox");
        }

        String activeBrowser = activePlaywrightBrowser();
        boolean replacementRequired =
                !activeBrowser.isEmpty() && !activeBrowser.equalsIgnoreCase(requestedBrowser);
        if (replacementRequired && !bool(body, "confirmReplace")) {
            String activeBrowserLabel =
                    "unknown".equals(activeBrowser) ? "An existing" : "A " + activeBrowser;
            Map<String, Object> response = failure(
                    activeBrowserLabel + " Playwright browser is currently running.");
            response.put("confirmationRequired", true);
            response.put("activeBrowser", activeBrowser);
            response.put("requestedBrowser", requestedBrowser);
            response.put(
                    "warning",
                    "Continuing closes the current shared Playwright browser before changing the configuration.");
            return response;
        }

        if (replacementRequired && !BotJobDetailsWorkspaceHost.getInstance().canCloseWorkspace()) {
            Map<String, Object> response = failure(
                    "Stop the active Bot Job or Page Scanner operation before replacing the browser.");
            response.put("confirmationRequired", false);
            response.put("activeBrowser", activeBrowser);
            response.put("requestedBrowser", requestedBrowser);
            return response;
        }

        String previousConfiguredBrowser =
                arPropertyManager.getProperty(ARPropertyEnum.BROWSER);
        try {
            arPropertyManager.setPropertyChecked(
                    ARPropertyEnum.BROWSER.getValue(), requestedBrowser);
        } catch (IOException persistenceFailure) {
            log.error(
                    "Could not persist browser {} to {}",
                    requestedBrowser,
                    arPropertyManager.getConfigurationFileName(),
                    persistenceFailure);
            return failure("Browser configuration could not be written to the active config file");
        }

        if (replacementRequired) {
            try {
                ARWebDriver.getInstance().closeBrowser();
            } catch (RuntimeException closeFailure) {
                log.error(
                        "Could not close the active {} Playwright browser before selecting {}",
                        activeBrowser,
                        requestedBrowser,
                        closeFailure);
                boolean rollbackFailed = false;
                try {
                    arPropertyManager.setPropertyChecked(
                            ARPropertyEnum.BROWSER.getValue(),
                            previousConfiguredBrowser == null ? "" : previousConfiguredBrowser);
                } catch (IOException rollbackFailure) {
                    rollbackFailed = true;
                    closeFailure.addSuppressed(rollbackFailure);
                    log.error(
                            "Could not restore browser configuration {} after close failure",
                            previousConfiguredBrowser,
                            rollbackFailure);
                }
                Map<String, Object> response =
                        failure("The active Playwright browser could not be closed safely.");
                response.put("confirmationRequired", false);
                response.put("activeBrowser", activeBrowser);
                response.put("requestedBrowser", requestedBrowser);
                response.put("rollbackFailed", rollbackFailed);
                return response;
            }
        }

        Map<String, Object> response = configResponse("Browser updated to " + requestedBrowser);
        response.put("browser", requestedBrowser);
        response.put("activeBrowser", activePlaywrightBrowser());
        response.put("browserClosed", replacementRequired);
        response.put("configFile", arPropertyManager.getConfigurationFileName());
        return response;
    }

    public Map<String, Object> deleteAllJobs(JsonObject body) {
        String selectedDb = str(body, "databaseType");
        String savedDb = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);
        if (!dbMatches(savedDb, selectedDb)) {
            return failure("The selected database type does not match the saved database");
        }
        try {
            performDataBase.changeDbConnection();
        } catch (SQLException error) {
            return failure("Database connection failed: " + error.getMessage());
        }
        if (!performDataBase.deleteAllJobDetails(savedDb)) {
            return failure("Not possible to delete all job details");
        }
        RuntimeVariableMemoryRegistry.getInstance().clearAll();
        performDataBase.loadQuickBotJobs();
        pushMainDashboard();
        return configResponse("All job details deleted");
    }

    public Map<String, Object> openOrganizations() {
        presentation().openOrganizations();
        return configResponse("Organizations opened");
    }

    public Map<String, Object> loadGenFlowPrompt() {
        String content = performDataBase.loadAiPrompt("GEN_FLOW");
        if (content == null) {
            return failure("No GEN_FLOW prompt found in the database");
        }
        Map<String, Object> response = ok("GEN FLOW prompt loaded");
        response.put("content", content);
        return response;
    }

    public Map<String, Object> saveGenFlowPrompt(JsonObject body) {
        ErrorMessage error = performDataBase.updateAiPrompt("GEN_FLOW", str(body, "content"));
        if (error != null) {
            return failure("Save GEN FLOW prompt failed", error);
        }
        return ok("GEN FLOW prompt saved");
    }

    public Map<String, Object> cancel() {
        presentation().closeModal();
        return ok("Cancelled");
    }

    private Map<String, Object> validate(JsonObject config) {
        Map<String, Object> errors = new LinkedHashMap<>();
        require(errors, config, "pathLicense", "License Path must be filled");
        require(errors, config, "pathExcel", "Excel Path must be filled");
        require(errors, config, "pathLog", "Log Path must be filled");
        require(errors, config, "pathDb", "Database Path must be filled");
        require(errors, config, "pathReport", "Reports Path must be filled");
        require(errors, config, "pathPriority", "Priority Path must be filled");
        require(errors, config, "pathEngine", "AR Engine Path must be filled");
        require(errors, config, "pathWebDriver", "Web Driver Path must be filled");
        require(errors, config, "pathAppium", "Appium Path must be filled");
        require(errors, config, "pathPlugins", "Plugins Path must be filled");
        if (!errors.isEmpty()) {
            Map<String, Object> response = failure("Validation failed");
            response.put("errors", errors);
            return response;
        }
        return ok("Validation passed");
    }

    private Map<String, Object> configResponse(String message) {
        Map<String, Object> response = ok(message);
        response.put("config", currentConfig());
        response.put("options", options());
        response.put("organizations", organizationRows());
        response.put("botJobs", mainDashboardService.dashboardRows());
        response.put("dbStatus", performDataBase.isConnDBWorks());
        return response;
    }

    private Map<String, Object> currentConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("browser", prop(ARPropertyEnum.BROWSER));
        config.put("databaseType", prop(ARPropertyEnum.DATABASE_TYPE));
        config.put("pathLicense", prop(ARPropertyEnum.PATH_LICENSE));
        config.put("pathExcel", prop(ARPropertyEnum.PATH_EXCEL));
        config.put("pathLog", prop(ARPropertyEnum.PATH_LOG));
        config.put("pathDb", prop(ARPropertyEnum.PATH_DB));
        config.put("pathReport", prop(ARPropertyEnum.PATH_REPORT));
        config.put("pathPriority", prop(ARPropertyEnum.PATH_PRIORITY));
        config.put("pathEngine", prop(ARPropertyEnum.PATH_ENGINE));
        config.put("pathWebDriver", prop(ARPropertyEnum.PATH_WEBDRIVER));
        config.put("pathAppium", prop(ARPropertyEnum.PATH_APPIUM));
        config.put("pathPlugins", prop(ARPropertyEnum.PATH_PLUGINS));
        config.put("urlPlugins", prop(ARPropertyEnum.URL_PLUGINS));
        config.put("dbUrl", prop(ARPropertyEnum.DB_URL));
        config.put("dbUser", prop(ARPropertyEnum.DB_USER));
        config.put("dbPwd", prop(ARPropertyEnum.DB_PWD));
        config.put("aiApiKey", prop(ARPropertyEnum.AI_API_KEY));
        config.put("aiEndpoint", prop(ARPropertyEnum.AI_ENDPOINT));
        config.put("aiModel", prop(ARPropertyEnum.AI_MODEL));
        config.put("aiMaxBlocks", prop(ARPropertyEnum.AI_MAX_BLOCKS));
        return config;
    }

    private Map<String, Object> options() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("browsers", Arrays.asList(ARConstants.CHROME, ARConstants.EDGE, ARConstants.FIREFOX));
        options.put("databaseTypes", Arrays.asList(ARConstants.ACCESS, ARConstants.POSTGRES, ARConstants.SQLITE));
        return options;
    }

    private List<Map<String, Object>> organizationRows() {
        return performLists.getListHomeBanking().stream().map(org -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", org.getId());
            row.put("name", org.getName());
            row.put("activeJobs", org.getJobs());
            row.put("url", org.getUrl());
            return row;
        }).collect(Collectors.toList());
    }

    private ErrorMessage reloadLists() {
        ErrorMessage error = performDBEngine.loadHomeBanking(null);
        if (error == null) {
            error = performDBEngine.loadHomeUrls(null);
        }
        if (error == null) {
            error = performDataBase.loadQuickBotJobs();
        }
        return error;
    }

    private ErrorMessage runLegacyPerTableRestore(Connection conn, String folder, String date) {
        ErrorMessage error = performBackup.restoreHomeBanking(conn, folder + File.separator + "backup_home_banking_" + date + ".sql");
        if (error == null) error = performBackup.restoreHomeUrl(conn, folder + File.separator + "backup_home_url_" + date + ".sql");
        if (error == null) error = performBackup.restoreBotJob(conn, folder + File.separator + "backup_bot_job_" + date + ".sql", null, null, null);
        if (error == null) error = performBackup.restoreBlock(conn, folder + File.separator + "backup_block_" + date + ".sql", null);
        if (error == null) error = performBackup.restoreInstruction(conn, folder + File.separator + "backup_instruction_" + date + ".sql", null);
        if (error == null) error = performBackup.restoreVariable(conn, folder + File.separator + "backup_variable_" + date + ".sql", null);
        if (error == null) error = performBackup.restoreUpdateInstruction(conn, null);
        if (error == null) error = performBackup.restoreReference(conn, folder + File.separator + "backup_reference_" + date + ".sql", null);
        if (error == null) error = performBackup.restoreComponentBlock(conn, folder + File.separator + "backup_component_block_" + date + ".sql");
        if (error == null) error = performBackup.restoreComponentInstruction(conn, folder + File.separator + "backup_component_instruction_" + date + ".sql");
        if (error == null) error = performBackup.restoreComponentVariable(conn, folder + File.separator + "backup_component_variable_" + date + ".sql");
        if (error == null) error = performBackup.restoreComponentUpdateInstruction(conn);
        if (error == null) error = performBackup.restoreComponentReference(conn, folder + File.separator + "backup_component_reference_" + date + ".sql");
        return error;
    }

    private void pushMainDashboard() {
        try {
            webSocketSessionManager.sendMessageJson(
                    -1, "mainDashboard", gson.toJson(mainDashboardService.list()), "mainDashboard.listResponse");
        } catch (Exception error) {
            log.warn("Config dashboard refresh push failed: {}", error.getMessage());
        }
    }

    private void closeAllScenes() {
        sceneShutdownService.closeAll();
    }

    private ConfigPresentation presentation() {
        return ConfigPresentationRegistry.getInstance().current();
    }

    private String prop(ARPropertyEnum property) {
        String value = arPropertyManager.getProperty(property);
        return value == null ? "" : value;
    }

    private void put(Map<String, String> updates, ARPropertyEnum property, String value) {
        updates.put(property.getValue(), value == null ? "" : value.trim());
    }

    private void require(Map<String, Object> errors, JsonObject config, String field, String message) {
        if (Strings.isNullOrEmpty(str(config, field))) {
            errors.put(field, message);
        }
    }

    private boolean dbMatches(String savedDb, String selectedDb) {
        return savedDb != null && selectedDb != null && savedDb.trim().equalsIgnoreCase(selectedDb.trim());
    }

    private String supportedBrowser(String requested) {
        for (String supported : Arrays.asList(ARConstants.CHROME, ARConstants.EDGE, ARConstants.FIREFOX)) {
            if (supported.equalsIgnoreCase(requested)) return supported;
        }
        return "";
    }

    private String activePlaywrightBrowser() {
        ARPlaywrightDriver driver = ARWebDriver.getInstance().currentPlaywrightDriver();
        if (driver == null) return "";
        try {
            return driver.activeBrowserType();
        } catch (RuntimeException unavailable) {
            log.debug("Active Playwright browser type is unavailable: {}", unavailable.getMessage());
            return "unknown";
        }
    }

    private String dbDialectSlug(String dataBaseType) {
        if (dataBaseType == null) return "db";
        String t = dataBaseType.trim();
        if (t.equalsIgnoreCase("TEXT")) return "sqlite";
        if (t.equalsIgnoreCase("Access")) return "access";
        if (t.equalsIgnoreCase("Postgres") || t.equalsIgnoreCase("PostGres")) return "postgres";
        return "db";
    }

    private Map<String, Object> ok(String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("message", message);
        return response;
    }

    private Map<String, Object> failure(String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", false);
        response.put("message", message);
        response.put("config", currentConfig());
        response.put("organizations", organizationRows());
        return response;
    }

    private Map<String, Object> failure(String message, ErrorMessage error) {
        Map<String, Object> response = failure(message);
        if (error != null) {
            response.put("error", error);
        }
        return response;
    }

    private Map<String, Object> correlatePathResponse(
            JsonObject body, Map<String, Object> response) {
        String requestId = str(body, "requestId");
        if (!requestId.isEmpty()) {
            response.put("requestId", requestId);
        }
        String purpose = str(body, "purpose");
        if ("config".equals(purpose)
                || "backup".equals(purpose)
                || "restore".equals(purpose)) {
            response.put("purpose", purpose);
        }
        return response;
    }

    private String str(JsonObject body, String field) {
        try {
            if (body == null || !body.has(field) || body.get(field).isJsonNull()) {
                return "";
            }
            return body.get(field).getAsString().trim();
        } catch (Exception ignore) {
            return "";
        }
    }

    private boolean bool(JsonObject body, String field) {
        try {
            return body != null
                    && body.has(field)
                    && !body.get(field).isJsonNull()
                    && body.get(field).getAsBoolean();
        } catch (Exception ignore) {
            return false;
        }
    }
}
