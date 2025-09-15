package com.allinweb.ch;

import com.allinweb.ch.component.scene.ARConfigurationScene;
import com.allinweb.ch.component.scene.ARLicenseScene;
import com.allinweb.ch.component.scene.ARMainScene;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformInitializer;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.license.LicenceVal;
import com.allinweb.ch.license.LicenseManager;
import com.allinweb.ch.socket.ARWebSocketServer;
import com.allinweb.ch.socket.ARWebSocketServerIP;
import com.allinweb.ch.socket.WebSocketSessionManager;
import com.allinweb.ch.util.*;
import com.google.common.base.Strings;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARControlPanel extends Application {

    private static final SuppressHsqldbLogs suppressHsqldbLogs = SuppressHsqldbLogs.getInstance();
    private static final LogControl logControl = LogControl.getInstance();
    private static final PerformMessage performMessage;
    private static final ARPropertyManager arPropertyManager;
    private static final PerformDataBase performDataBase;
    private static final PerformInitializer performInitializer;
    private static final ARLicenseScene arLicenseScene;
    private static final ARConfigurationScene arConfigurationScene;
    private static final ARMainScene arMainScene;
    private static WebSocketSessionManager webSocketSessionManager = WebSocketSessionManager.getInstance();
    private static ARWebSocketServerIP arWebSocketServerIP;
    private static ARWebSocketServer arWebSocketServer; // Static block to initialize
    private static String defaultConfigurationFileName = ARConstants.USER_PATH + ARConstants.FILE_DEFAULT_CONFIG;
    private static boolean isEnabledLicence = true;

    static {
        performDataBase = PerformDataBase.getInstance();
        performInitializer = PerformInitializer.getInstance();
        performMessage = PerformMessage.getInstance();
        arPropertyManager = ARPropertyManager.getInstance();
        arLicenseScene = ARLicenseScene.getInstance();
        arConfigurationScene = ARConfigurationScene.getInstance();
        arMainScene = ARMainScene.getInstance();
    }

    public static void main(String[] args) {
        List<String> arguments = Arrays.asList(args);
        int chosenPort = getInitialPort();
        int chosenPortIP = getInitialPort();
        System.setProperty("ARWebChosenPort", String.valueOf(chosenPort));
        System.setProperty("ARWebChosenPortIP", String.valueOf(chosenPortIP));

        if (arguments.contains("-c")) {
            int configurationValueIndex = arguments.indexOf("-c") + 1;
            String configurationValue = arguments.get(configurationValueIndex);
            try {
                System.setProperty("ARWebConfig", configurationValue);
            } catch (Exception ignore) {

            }
            // Prevention if  System.setProperty(...) has no permission access
            arPropertyManager.setConfigurationFileName(configurationValue);

            File configurationFile = new File(configurationValue);
            try (FileInputStream conf = new FileInputStream(configurationFile)) {
                arPropertyManager.loadProperties(conf);
                String porSocketInUse = System.getProperty("ARWebChosenPort");
                arPropertyManager.setProperty(ARPropertyEnum.PORT_SOCKET.getValue(), porSocketInUse);
                // Make path available to Logback via System property
                setLogPath();
                logControl.enableLogging();
                licenseControl();
                initializeServers();
            } catch (Exception error) {
                if (!configurationFile.exists()) {
                    arPropertyManager.createDefaultProperties(configurationFile);
                }
                Platform.runLater(() -> {
                    arConfigurationScene.initializeLicense(isEnabledLicence);
                    arConfigurationScene.showModal();
                    licenseControl();
                });
            }

            log.info("Configuration file path: " + configurationValue);
        } else {
            try {
                System.setProperty("ARWebConfig", defaultConfigurationFileName);
            } catch (Exception ignore) {

            }
            arPropertyManager.setConfigurationFileName(defaultConfigurationFileName);
            File configurationFile = new File(defaultConfigurationFileName);
            try (FileInputStream conf = new FileInputStream(configurationFile)) {
                // reads from config.properties
                arPropertyManager.loadProperties(conf);
                // changes to ARWeb.config
                defaultConfigurationFileName = ARConstants.USER_PATH + ARConstants.FILE_AR_WEB_CONFIG;
                arPropertyManager.setConfigurationFileName(defaultConfigurationFileName);
                String porSocketInUse = System.getProperty("ARWebChosenPort");
                arPropertyManager.setProperty(ARPropertyEnum.PORT_SOCKET.getValue(), porSocketInUse);
                // Make path available to Logback via System property
                setLogPath();
                logControl.enableLogging();
                licenseControl();
                initializeServers();
            } catch (Exception error) {
                if (!configurationFile.exists()) {
                    arPropertyManager.createDefaultProperties(configurationFile);
                }
                Platform.runLater(() -> {
                    arConfigurationScene.showModal();
                    licenseControl();
                });
            }

            log.info("Configuration file path: " + defaultConfigurationFileName);
        }

        arPropertyManager.setProperty(ARPropertyEnum.VERSION.getValue(), "AR Web v4.2f Beta Test");
        arPropertyManager.setProperty(ARPropertyEnum.BUILD.getValue(), "Build: 12/09/2025"); //
    }

    private static void initializeServers() {
        arWebSocketServerIP = ARWebSocketServerIP.getInstance();
        arWebSocketServer = ARWebSocketServer.getInstance();
        performDataBase.callSocketLists();
    }

    private static void setLogPath() {
        // Set log path system property BEFORE logback init
        String logPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_LOG);
        File logDir = new File(logPath);
        if (!logDir.exists() && !logDir.mkdirs()) {
            log.error("❌ Failed to create log directory: " + logDir.getAbsolutePath());
            System.exit(1);
        }
        System.setProperty("LOG_PATH", logDir.getAbsolutePath());

        // After main logic, initialize logging
        LogbackInitializer.loadLogbackFromResources();
        System.setProperty("org.eclipse.jetty.LEVEL", "ON");
        // Now logback initializes with correct LOG_PATH
        log.info("Using log path: {}", logDir.getAbsolutePath());
    }

    private static void licenseControl() {
        arPropertyManager.setProperty(ARPropertyEnum.EXPIRATION.getValue(), getTodaysDate(-1));
        if (isEnabledLicence) {

            String licensePath = arPropertyManager.getProperty(ARPropertyEnum.PATH_LICENSE);
            if (Strings.isNullOrEmpty(licensePath)) {
                licensePath = System.getProperty("user.dir");
                arPropertyManager.setProperty(ARPropertyEnum.PATH_LICENSE.getValue(), licensePath);
            }

            AtomicReference<LicenceVal> license = new AtomicReference<>();

            try {
                license.set(LicenseManager.checkLicenseFile(licensePath));
            } catch (Exception error) {
                log.warn("Error reading/writing to the file: " + licensePath);
                //                performMessage.errorMessage(
                //                        "Error reading/writing to the file!",
                //                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Please
                // verify that you have permission to read/write.!</span>",
                //                        "<span style='color: #E65100; font-weight: bold;'>Attempted to
                // read/write:</span> <span style='font-weight: bold;'>"
                //                                + licensePath + "</span>",
                //                        "<span style='font-style: italic;'>Please ensure the application has the
                // necessary write permissions for the specified directory</span>",
                //                        "<span style='font-style: italic;'>Details: " + error.getMessage() +
                // "</span>",
                //                        0);
            }
            try {
                if (license.get().isMissing()) {
                    // If the license is not active, launch the license activation app
                    //                    Application.launch(LicenseActivationApp.class, args);
                    Platform.runLater(() -> {
                        arLicenseScene.showModal();
                        String finalLicensePath = arPropertyManager.getProperty(ARPropertyEnum.PATH_LICENSE);

                        try {
                            license.set(LicenseManager.checkLicenseFile(finalLicensePath));
                            if (license.get().isActive()) {
                                databaseControl();
                                // webSocketControl();

                                arMainScene.initialize(isEnabledLicence);
                                arMainScene.showModal();
                            } else {
                                licenseMessages(license.get());
                            }
                        } catch (Exception error) {
                            log.warn("Error reading/writing to the file: " + finalLicensePath);
                            //                            performMessage.errorMessage(
                            //                                    "Error reading/writing to the file!",
                            //                                    "<span style='color: #D32F2F; font-weight: bold;
                            // font-size: 1.1em;'>Please verify that you have permission to read/write.!</span>",
                            //                                    "<span style='color: #E65100; font-weight:
                            // bold;'>Attempted to read/write:</span> <span style='font-weight: bold;'>"
                            //                                            + finalLicensePath + "</span>",
                            //                                    "<span style='font-style: italic;'>Please ensure the
                            // application has the necessary write permissions for the specified directory</span>",
                            //                                    "<span style='font-style: italic;'>Details: " +
                            // error.getMessage() + "</span>",
                            //                                    0);
                        }
                    });

                } else {
                    license.set(LicenseManager.checkLicenseFile(licensePath));
                    if (license.get().isActive()) {
                        databaseControl();
                        // webSocketControl();

                        Platform.runLater(() -> {
                            arMainScene.initialize(isEnabledLicence);
                            arMainScene.showModal();
                        });

                    } else {
                        licenseMessages(license.get());
                    }
                }
            } catch (Exception error) {
                log.warn("Error reading/writing to the file: " + licensePath);
                //                performMessage.errorMessage(
                //                        "Error reading/writing to the file!",
                //                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Please
                // verify that you have permission to read/write.!</span>",
                //                        "<span style='color: #E65100; font-weight: bold;'>Attempted to
                // read/write:</span> <span style='font-weight: bold;'>"
                //                                + licensePath + "</span>",
                //                        "<span style='font-style: italic;'>Please ensure the application has the
                // necessary write permissions for the specified directory</span>",
                //                        "<span style='font-style: italic;'>Details: " + error.getMessage() +
                // "</span>",
                //                        0);

                log.info(error.getMessage());
            }
        } else {
            databaseControl();
            // If the license is disabled, directly proceed with the main application
            // Ensure launch(args) is only called once: JavaFX does not allow calling Application.launch() twice.
            //            launch();
            // webSocketControl();
            Platform.runLater(() -> {
                arMainScene.initialize(isEnabledLicence);
                arMainScene.showModal();
            });
            if (!performDataBase.isConnDBWorks()) {
                Platform.runLater(() -> {
                    arConfigurationScene.showModal();
                });
            }
        }
    }

    private static void licenseMessages(LicenceVal licenseStatus) {
        String msgValid = "The license file is valid and the application is authorized for use.";
        String msgNextStep = "You can now proceed with normal application usage.";

        String msgColor = "#0277BD";
        String msgColorExp = "#000080";
        if (!licenseStatus.equals(LicenceVal.VALID)) {
            msgValid = "The license file is not valid and the application is not authorized for use.";
            msgNextStep = "Application access is restricted. Please obtain a valid license to continue.";
            msgColor = "#C62828"; // Soft, elegant red tone
            msgColorExp = "#C62828";
        }

        performMessage.showCustomModalDialogDragWin11(
                "License Status Verification",
                "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>License status has been successfully verified.</span>",
                "<span style='color: " + msgColor + "; font-weight: bold;'>" + msgValid + "</span>",
                "<span style='color: #E65100; font-weight: bold;'>Current license status:</span> <span style='font-weight: bold;'>"
                        + licenseStatus.getStaus() + "</span>",
                "Expiration: <span style='color: " + msgColorExp + "; font-weight: bold;'>"
                        + arPropertyManager.getProperty(ARPropertyEnum.EXPIRATION) + "</span>",
                false,
                "OK",
                null,
                0);
        System.exit(0);
    }

    public static String getTodaysDate(int day) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(day);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        return yesterday.format(formatter);
    }

    private static void databaseControl() {
        String dataBaseType = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);
        try {
            performDataBase.initialize(dataBaseType);

        } catch (Exception error) {
            log.error("Error Database Connections: " + error.getMessage());
        }

        if ("Postgres".equalsIgnoreCase(dataBaseType)) {
            // Postgres-specific logic
            performDataBase.POSTGRES_DB = true;
        } else if ("TEXT".equalsIgnoreCase(dataBaseType)) {
            // SQLite-specific logic
            performDataBase.SQLITE_DB = true;
        } else if ("Access".equalsIgnoreCase(dataBaseType)) {
            // Access-specific logic
            performDataBase.ACCESS_DB = true;
        }

        if (performDataBase.POSTGRES_DB) {
            try {
                if (performInitializer.doesNotInstructionTableExist(performDataBase.getConnection())) {

                    if (performDataBase.isConnDBWorks()) {
                        //            createTableOpenAIVector();
                        //            createTableLLama2AIVector();
                        performInitializer.initializeMainDatabasePostgres();
                    }
                }
            } catch (Exception error) {
                log.error("Error connection with Postgres: " + error.getMessage());
            }

            //            performDataBase.dropPostGresSequences();
            try {

                Connection conn = performDataBase.getConnection();
                if (conn != null) {
                    log.info("Postgres Database connected!");
                }
            } catch (Exception error) {
            }

        } else if (performDataBase.ACCESS_DB) {
            String dbPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);

            File dbFile = new File(dbPath + ARConstants.FILE_NAME_ACCESS);

            try {

                if (!dbFile.exists()
                        && performInitializer.doesNotInstructionTableExistAccess(performDataBase.getConnection())) {
                    if (performDataBase.isConnDBWorks()) {
                        performInitializer.initialize();

                        ErrorMessage errorMessage = performInitializer.initializeMainDatabaseAccess(dbFile);
                        if (errorMessage != null) {
                            log.error("Database Creation Error: " + errorMessage.getErrorMessage());

                            performMessage.errorMessageOperationFailed(errorMessage);
                        }
                    }
                } else {
                    //                    performDataBase.updateColumns();

                    //                performDataBase.disableForeignKeyConstraints(dbUrl);
                    //                                    performDataBase.updateTableAccess(dbUrl, dbFile);
                    //                                    performDataBase.updateDatabaseSchema(dbUrl, dbFile);

                    log.info(String.format("Database '%s' already exists!", dbFile.getName()));
                }

            } catch (Exception error) {
                log.error("Database Creation Error: " + error.getMessage());
                performMessage.errorMessage(
                        "Configuration Needed", // Using configurationFileName as the title
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Critical: Set the path for the Database!</span>",
                        "<span style='color: #2E7D32; font-weight: bold;'>The Path for Database is Blank!</span>",
                        "<span style='font-weight: bold;'>Please configure the application before use.</span>.",
                        "<span style='font-weight: bold;'>" + dbPath + ARConstants.FILE_NAME_ACCESS + "</span>.",
                        0);
                System.exit(0);
            }

            try {
                Connection conn = performDataBase.getConnection();
                if (conn != null) {
                    log.info("Access Database connected!");
                }
            } catch (Exception error) {
                log.error("Error Access: " + error.getMessage());
            }

        } else if (performDataBase.SQLITE_DB) {
            String dbPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);

            File dbFile = new File(dbPath + ARConstants.FILE_NAME_SQLITE);

            try {

                if (!dbFile.exists()
                        && performInitializer.doesNotInstructionTableExistSQLITE(performDataBase.getConnection())) {
                    if (performDataBase.isConnDBWorks()) {
                        performInitializer.initialize();
                        performInitializer.initializeMainDatabaseSQLite(dbFile);
                    }
                } else {
                    //                performDataBase.disableForeignKeyConstraints(dbUrl);
                    //                                    performDataBase.updateTableAccess(dbUrl, dbFile);
                    //                                    performDataBase.updateDatabaseSchema(dbUrl, dbFile);

                    log.info(String.format("Database '%s' already exists!", dbFile.getName()));
                }

            } catch (Exception error) {
                log.error("Database Creation Error: " + error.getMessage());
                performMessage.errorMessage(
                        "Configuration Needed", // Using configurationFileName as the title
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>Critical: Set the path for the Database!</span>",
                        "<span style='color: #2E7D32; font-weight: bold;'>The Path for Database is Blank!</span>",
                        "<span style='font-weight: bold;'>Please configure the application before use.</span>.",
                        "<span style='font-weight: bold;'>" + dbPath + ARConstants.FILE_NAME_SQLITE + "</span>.",
                        0);
                System.exit(0);
            }

            try {
                Connection conn = performDataBase.getConnection();
                if (conn != null) {
                    log.info("SQLite Database connected!");
                }
            } catch (Exception error) {
                log.error("Error SQLite: " + error.getMessage());
            }
        }

        if (performDataBase.dbFailed) {
            log.error(
                    "Database connection Failed: {} -> {} ",
                    performDataBase.getErrorMessage().getErrorHeader(),
                    performDataBase.getErrorMessage().getErrorMessage());
            performMessage.errorMessage(
                    "Database connection Failed",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>An error occurred during the Database connection.</span>",
                    "<span style='font-weight: bold;'>"
                            + performDataBase.getErrorMessage().getErrorHeader() + "</span>.",
                    "<span style='color: #E65100; font-weight: bold;'>Please ensure the Database connections are correct.</span>",
                    "<span style='font-style: italic;'>Details: "
                            + performDataBase.getErrorMessage().getErrorMessage() + "</span>",
                    0);
        }
    }

    @Override
    public void start(Stage stage) throws Exception {
        //        ARMainScene primaryStage = new ARMainScene();
        //        primaryStage.show();
    }

    private static int getInitialPort() {
        int defaultFixedPort = 54525; // A known default port if no ephemeral or previous setting works
        int chosenPort;

        try (ServerSocket tempSocket = new ServerSocket(0)) {
            tempSocket.setReuseAddress(true); // Allow immediate reuse of the address
            chosenPort = tempSocket.getLocalPort();
            //            loggerlog.info("Found available ephemeral port: " + chosenPort);
        } catch (IOException e) {
            // If finding an ephemeral port fails, log the warning and fall back to the fixed default
            //            loggerlog.warn("Could not find an ephemeral port. Falling back to default fixed port: " +
            // defaultFixedPort + ". Error: " + e.getMessage());
            chosenPort = defaultFixedPort;
        }

        // 2. Persist the chosen port to properties
        System.setProperty("ARWebChosenPort", String.valueOf(chosenPort));
        //        arPropertyManager.setProperty(ARPropertyEnum.PORT_SOCKET.getValue(), String.valueOf(chosenPort));
        //        loggerlog.info("Set " + ARPropertyEnum.PORT_SOCKET.getValue() + " to: " + chosenPort + " in
        // properties.");

        return chosenPort;
    }
}
