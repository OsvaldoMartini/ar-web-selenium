package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.*;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.*;
import com.allinweb.ch.license.LicenceVal;
import com.allinweb.ch.license.LicenseManager;
import com.allinweb.ch.model.BotJobLoadDTO;
import com.allinweb.ch.model.HomeBankingLoadDTO;
import com.allinweb.ch.util.*;
import com.google.common.base.Strings;
import java.awt.*;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import javax.swing.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARConfigurationPane extends ARPane {

    private static final ARComponentBuilder builder = ARComponentBuilder.getInstance();
    private static final int SECONDS = 3;

    private static final ARPropertyManager arPropertyManager;
    private static final ARNewHomeBankingScene arNewHomeBankingScene;
    private static final PerformMessage performMessage;
    private static final PerformLists performLists;
    private static final PerformDBEngine performDBEngine;
    private static final PerformDataBase performDataBase;
    private static final PerformBackup performBackup;
    private static final PerformInitializer performInitializer;
    private static final ARWebDriver arWebDriver = ARWebDriver.getInstance();
    //    private static final ARScannedElementScene arScannedElementScene;
    private static final ARViewBotJobScene arViewBotJobScene;
    //    private static final ARNewCommandScene arNewCommandScene;
    private static final ARElementValueScene arElementValueScene;
    private static final ARNewBotJobScene arNewBotJobScene;
    protected static volatile ARConfigurationPane instance;

    static {
        //        arScannedElementScene = ARScannedElementScene.getInstance();
        //        arNewCommandScene = ARNewCommandScene.getInstance();
        arElementValueScene = ARElementValueScene.getInstance();
        arViewBotJobScene = ARViewBotJobScene.getInstance();
        arNewBotJobScene = ARNewBotJobScene.getInstance();

        arPropertyManager = ARPropertyManager.getInstance();
        performMessage = PerformMessage.getInstance();
        performLists = PerformLists.getInstance();
        performDataBase = PerformDataBase.getInstance();
        performDBEngine = PerformDBEngine.getInstance();
        arNewHomeBankingScene = ARNewHomeBankingScene.getInstance();
        performBackup = PerformBackup.getInstance();
        performInitializer = PerformInitializer.getInstance();
    }

    // UI components (Swing)
    JLabel title;
    JLabel pathExcelLabel;
    JLabel pathLicenseLabel;
    JLabel pathLogLabel;
    JLabel dbUrlLabel;
    JLabel dbUserLabel;
    JLabel dbPwdLabel;
    JLabel pathAccessDBLabel;
    JLabel databaseLabel;
    JLabel pathReportLabel;
    JLabel pathPriorityLabel;
    JLabel pathEngineLabel;
    JLabel browserLabel;
    JLabel reloadDBLabel;
    JLabel backupDBLabel;
    JLabel restoreDateLabel;
    JLabel deleteAllDBLabel;
    JLabel insertSitesLabel;
    JLabel pathWebDriverLabel;
    JLabel pathAppiumLabel;

    JTextField pathExcel;
    JTextField pathLicense;
    JTextField pathLog;
    JTextField pathAccessDB;
    JTextField pathReport;
    JTextField pathPriority;
    JTextField dbUrl;
    JTextField dbUser;
    JTextField dbPwd;
    JTextField pathEngine;
    JTextField pathWebDriver;
    JTextField pathAppium;

    JComboBox<String> browserChoiceBox = new JComboBox<>();
    JComboBox<String> databaseChoiceBox = new JComboBox<>();

    JButton pathExcelButton;
    JButton pathLicenseButton;
    JButton pathLogButton;
    JButton pathAccessDBButton;
    JButton pathReportButton;
    JButton pathPriorityButton;
    JButton pathEngineButton;
    JButton pathWebDriverButton;
    JButton pathAppiumButton;
    JButton reloadDBButton;
    JButton backupDBButton;
    JButton restoreDBButton;
    JButton deleteAllDBButton;
    JButton insertSitesdButton;

    JPanel backupRestoreGroup;
    JTextField restoreDateField;

    JPanel pathGroup;
    JPanel mainPane;

    private JList<BotJobLoadDTO> viewBotJobListView;
    private JDialog modalDialog; // replaces Stage
    private boolean isEnabledLicence;
    private String previousDB;
    private String previousDBUrl;

    private ExecutorService executorService;
    private JList<HomeBankingLoadDTO> homeBankingListView;

    private ARConfigurationPane() {
        super();
    }

    public static ARConfigurationPane getInstance() {
        if (instance == null) {
            synchronized (ARConfigurationPane.class) {
                if (instance == null) {
                    instance = new ARConfigurationPane();
                }
            }
        }
        return instance;
    }

    public void initialize(JDialog modalDialog, JList<BotJobLoadDTO> viewBotJobListView, boolean isEnabledLicence) {
        this.isEnabledLicence = isEnabledLicence;
        this.modalDialog = modalDialog;
        this.viewBotJobListView = viewBotJobListView;
    }

    @Override
    public JPanel getPaneReference() {
        return mainPane;
    }

    @Override
    public JPanel createPane() {
        if (mainPane == null) {
            initUIComponents();
            initUIBehaviour();
        }
        return mainPane;
    }

    @Override
    public void initUIComponents() {

        this.previousDB = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);
        this.previousDBUrl = arPropertyManager.getProperty(ARPropertyEnum.DB_URL);

        title = new JLabel("Configuration");
        title.setOpaque(true);
        title.setBackground(new Color(65, 105, 225)); // RoyalBlue
        title.setForeground(Color.WHITE);
        title.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        insertSitesdButton = new JButton("Insert Organizations");

        if (performDataBase.isConnDBWorks()) {
            ErrorMessage errorMessage = performDBEngine.loadHomeBanking(null);
            if (errorMessage != null) {
                performMessage.errorMessageOperationFailed(errorMessage);
            }
        }

        homeBankingListView = new JList<>(performLists.getListHomeBanking().toArray(new HomeBankingLoadDTO[0]));
        JScrollPane homeBankingScroll = new JScrollPane(homeBankingListView);
        homeBankingScroll.setPreferredSize(new Dimension(200, 100));

        pathLicenseLabel = new JLabel("License Path:");
        pathLicense = createPathTextField(ARPropertyEnum.PATH_LICENSE);
        pathLicenseButton = createPathButton();

        JPanel licenseGroup = new JPanel(new BorderLayout(5, 5));
        licenseGroup.add(pathLicense, BorderLayout.CENTER);
        licenseGroup.add(pathLicenseButton, BorderLayout.EAST);

        pathExcelLabel = new JLabel("Excel Path:");
        pathExcel = createPathTextField(ARPropertyEnum.PATH_EXCEL);
        pathExcelButton = createPathButton();
        JPanel excelGroup = new JPanel(new BorderLayout(5, 5));
        excelGroup.add(pathExcel, BorderLayout.CENTER);
        excelGroup.add(pathExcelButton, BorderLayout.EAST);

        pathLogLabel = new JLabel("Log Path:");
        pathLog = createPathTextField(ARPropertyEnum.PATH_LOG);
        pathLogButton = createPathButton();
        JPanel logGroup = new JPanel(new BorderLayout(5, 5));
        logGroup.add(pathLog, BorderLayout.CENTER);
        logGroup.add(pathLogButton, BorderLayout.EAST);

        pathAccessDBLabel = new JLabel("Access Database Path:");
        pathAccessDB = createPathTextField(ARPropertyEnum.PATH_DB);
        pathAccessDBButton = createPathButton();
        JPanel dbPathGroup = new JPanel(new BorderLayout(5, 5));
        dbPathGroup.add(pathAccessDB, BorderLayout.CENTER);
        dbPathGroup.add(pathAccessDBButton, BorderLayout.EAST);

        browserLabel = new JLabel("Browser");
        databaseLabel = new JLabel("DB Type");
        backupDBLabel = new JLabel("Backup / Restore DB");
        restoreDateLabel = new JLabel("Date Restore");
        reloadDBLabel = new JLabel("Reload DB");
        deleteAllDBLabel = new JLabel("Delete ALL DB");
        insertSitesLabel = new JLabel("Insert Sites");

        backupDBButton = new JButton("Backup");
        restoreDBButton = new JButton("Restore");

        backupDBButton.setEnabled(true);

        restoreDateField = new JTextField(LocalDate.now().format(DateTimeFormatter.ISO_DATE), 10);

        backupRestoreGroup = new JPanel();
        backupRestoreGroup.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0));
        backupRestoreGroup.add(backupDBButton);
        backupRestoreGroup.add(restoreDBButton);
        backupRestoreGroup.add(restoreDateField);

        reloadDBButton = new JButton("Reload Configs");
        deleteAllDBButton = new JButton("Delete DB");

        browserChoiceBox.addItem(ARConstants.CHROME);
        browserChoiceBox.addItem(ARConstants.EDGE);
        browserChoiceBox.addItem(ARConstants.FIREFOX);

        databaseChoiceBox.addItem(ARConstants.ACCESS);
        databaseChoiceBox.addItem(ARConstants.POSTGRES);
        databaseChoiceBox.addItem(ARConstants.SQLITE);

        JPanel buttonRow = new JPanel();
        buttonRow.setLayout(new GridLayout(2, 7, 5, 2)); // rough approximation

        buttonRow.add(browserLabel);
        buttonRow.add(databaseLabel);
        buttonRow.add(reloadDBLabel);
        buttonRow.add(backupDBLabel);
        buttonRow.add(restoreDateLabel);
        buttonRow.add(deleteAllDBLabel);
        buttonRow.add(insertSitesLabel);

        buttonRow.add(browserChoiceBox);
        buttonRow.add(databaseChoiceBox);
        buttonRow.add(reloadDBButton);
        buttonRow.add(backupRestoreGroup);
        buttonRow.add(new JLabel()); // filler under "Date Restore" (we already use field in group)
        buttonRow.add(deleteAllDBButton);
        buttonRow.add(insertSitesdButton);

        dbUrlLabel = new JLabel("Database URL:");
        dbUrl = createPathTextField(ARPropertyEnum.DB_URL);

        pathReportLabel = new JLabel("Report Path:");
        pathReport = createPathTextField(ARPropertyEnum.PATH_REPORT);
        pathReportButton = createPathButton();
        JPanel reportGroup = new JPanel(new BorderLayout(5, 5));
        reportGroup.add(pathReport, BorderLayout.CENTER);
        reportGroup.add(pathReportButton, BorderLayout.EAST);

        pathPriorityLabel = new JLabel("Priority Path:");
        pathPriority = createPathTextField(ARPropertyEnum.PATH_PRIORITY);
        pathPriorityButton = createPathButton();
        JPanel priorityGroup = new JPanel(new BorderLayout(5, 5));
        priorityGroup.add(pathPriority, BorderLayout.CENTER);
        priorityGroup.add(pathPriorityButton, BorderLayout.EAST);

        dbUserLabel = new JLabel("Database User");
        dbPwdLabel = new JLabel("Database Password");
        dbUser = createPathTextField(ARPropertyEnum.DB_USER);
        dbPwd = createPathTextField(ARPropertyEnum.DB_PWD);

        JPanel dbUserPwdGroup = new JPanel(new GridLayout(2, 2, 5, 2));
        dbUserPwdGroup.add(dbUserLabel);
        dbUserPwdGroup.add(dbPwdLabel);
        dbUserPwdGroup.add(dbUser);
        dbUserPwdGroup.add(dbPwd);

        pathEngineLabel = new JLabel("Engine Path:");
        pathEngine = createPathTextField(ARPropertyEnum.PATH_ENGINE);
        pathEngineButton = createPathButton();
        JPanel engineGroup = new JPanel(new BorderLayout(5, 5));
        engineGroup.add(pathEngine, BorderLayout.CENTER);
        engineGroup.add(pathEngineButton, BorderLayout.EAST);

        pathWebDriverLabel = new JLabel("Web Driver Path:");
        pathWebDriver = createPathTextField(ARPropertyEnum.PATH_WEBDRIVER);
        pathWebDriverButton = createPathButton();
        JPanel driverGroup = new JPanel(new BorderLayout(5, 5));
        driverGroup.add(pathWebDriver, BorderLayout.CENTER);
        driverGroup.add(pathWebDriverButton, BorderLayout.EAST);

        pathAppiumLabel = new JLabel("Appium Path:");
        pathAppium = createPathTextField(ARPropertyEnum.PATH_APPIUM);
        pathAppiumButton = createPathButton();
        JPanel appiumGroup = new JPanel(new BorderLayout(5, 5));
        appiumGroup.add(pathAppium, BorderLayout.CENTER);
        appiumGroup.add(pathAppiumButton, BorderLayout.EAST);

        JLabel organizationsLabel = new JLabel("Organizations");
        organizationsLabel.setHorizontalAlignment(SwingConstants.CENTER);
        organizationsLabel.setFont(organizationsLabel.getFont().deriveFont(Font.BOLD));

        pathGroup = new JPanel();
        pathGroup.setLayout(new BoxLayout(pathGroup, BoxLayout.Y_AXIS));
        pathGroup.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        pathGroup.add(pathLicenseLabel);
        pathGroup.add(licenseGroup);
        pathGroup.add(Box.createVerticalStrut(5));

        pathGroup.add(pathExcelLabel);
        pathGroup.add(excelGroup);
        pathGroup.add(Box.createVerticalStrut(5));

        pathGroup.add(pathLogLabel);
        pathGroup.add(logGroup);
        pathGroup.add(Box.createVerticalStrut(5));

        pathGroup.add(pathAccessDBLabel);
        pathGroup.add(dbPathGroup);
        pathGroup.add(Box.createVerticalStrut(5));

        pathGroup.add(pathReportLabel);
        pathGroup.add(reportGroup);
        pathGroup.add(Box.createVerticalStrut(5));

        pathGroup.add(pathPriorityLabel);
        pathGroup.add(priorityGroup);
        pathGroup.add(Box.createVerticalStrut(5));

        pathGroup.add(pathEngineLabel);
        pathGroup.add(engineGroup);
        pathGroup.add(Box.createVerticalStrut(5));

        pathGroup.add(pathWebDriverLabel);
        pathGroup.add(driverGroup);
        pathGroup.add(Box.createVerticalStrut(5));

        pathGroup.add(pathAppiumLabel);
        pathGroup.add(appiumGroup);
        pathGroup.add(Box.createVerticalStrut(5));

        pathGroup.add(dbUrlLabel);
        pathGroup.add(dbUrl);
        pathGroup.add(Box.createVerticalStrut(5));

        pathGroup.add(dbUserPwdGroup);
        pathGroup.add(Box.createVerticalStrut(10));

        pathGroup.add(buttonRow);
        pathGroup.add(Box.createVerticalStrut(10));

        pathGroup.add(organizationsLabel);
        pathGroup.add(homeBankingScroll);

        mainPane = new JPanel(new BorderLayout());
        mainPane.add(title, BorderLayout.NORTH);
        mainPane.add(pathGroup, BorderLayout.CENTER);
    }

    @Override
    public void initUIBehaviour() {

        databaseChoiceBox.addActionListener(e -> {
            String newVal = (String) databaseChoiceBox.getSelectedItem();
            if (ARConstants.ACCESS.equals(newVal)) {
                pathAccessDB.setEnabled(true);
                dbUrl.setEnabled(false);
                dbUser.setEnabled(false);
                dbPwd.setEnabled(false);
            } else {
                pathAccessDB.setEnabled(false);
                dbUrl.setEnabled(true);
                dbUser.setEnabled(true);
                dbPwd.setEnabled(true);
            }
        });

        if (performDataBase.isConnDBWorks()) {
            try (Connection conn = performDataBase.getConnection()) {
                if (conn != null) {
                    if (performLists.getListHomeBanking().isEmpty()) {
                        backupDBButton.setEnabled(false);
                    }
                }
            } catch (SQLException ignore) {
                log.info("Check if It Was Migrated! - Not Migrate Columns found!");
            }
        }

        insertSitesdButton.addActionListener(e -> {
            if (isEnabledLicence && !checkLicense()) {
                return;
            }
            HomeBankingLoadDTO homeBank = performLists.getFirstHomeBanking();
            arNewHomeBankingScene.initialize(homeBank);
            arNewHomeBankingScene.showModal(null); // Adjust to your Swing API
        });

        pathLicenseButton.addActionListener(e -> openChooserFor(pathLicense, true));
        pathExcelButton.addActionListener(e -> openChooserFor(pathExcel, true));
        pathLogButton.addActionListener(e -> openChooserFor(pathLog, true));
        pathAccessDBButton.addActionListener(e -> openChooserFor(pathAccessDB, true));
        pathReportButton.addActionListener(e -> openChooserFor(pathReport, true));
        pathPriorityButton.addActionListener(e -> openChooserFor(pathPriority, true));
        pathEngineButton.addActionListener(e -> openChooserFor(pathEngine, false));
        pathWebDriverButton.addActionListener(e -> openChooserFor(pathWebDriver, false));
        pathAppiumButton.addActionListener(e -> openChooserFor(pathAppium, true));

        String browser = arPropertyManager.getProperty(ARPropertyEnum.BROWSER);
        if (browser != null) {
            browserChoiceBox.setSelectedItem(browser);
        }

        String dbType = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);
        if (dbType == null) {
            databaseChoiceBox.setSelectedItem(ARConstants.ACCESS);
        } else {
            databaseChoiceBox.setSelectedItem(dbType);
        }

        reloadDBButton.addActionListener(e -> {
            try {
                saveConfigurations();
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        });

        backupDBButton.addActionListener(e -> runBackupScripts());
        restoreDBButton.addActionListener(e -> runRestoreScripts());
        deleteAllDBButton.addActionListener(e -> deleteAllDB());
    }

    private void runBackupScripts() {
        String dataBaseType = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"));

        if (dataBaseType.equalsIgnoreCase(((String) databaseChoiceBox.getSelectedItem()).trim())) {
            try {
                performDataBase.changeDbConnection();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        } else {
            performMessage.showCustomModalDialogDragWin11(
                    "Database Selection Mismatch ⚠️",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>The selected database type does not match the saved database!</span>",
                    "<span style='color: #1565C0; font-weight: bold;'>Please select the database that matches the saved type, or reload configurations to apply your selection.</span>",
                    "<span style='color: #6A1B9A; font-weight: bold;'>Selected Database:</span> "
                            + databaseChoiceBox.getSelectedItem(),
                    "<span style='color: #6A1B9A; font-weight: bold;'>Saved Database:</span> "
                            + dataBaseType + "<br/>"
                            + "<span style='color: #E65100; font-weight: bold;'>💡 Reminder:</span> Press the <span style='text-decoration: underline;'>Reload Configs</span> button to save and apply your database choice before continuing.",
                    false,
                    "OK",
                    null,
                    0);
            return;
        }

        if (dataBaseType.equalsIgnoreCase("ACCESS")) {
            String dbPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
            File dbFile = new File(dbPath + ARConstants.FILE_NAME_ACCESS);

            try {
                if (dbFile.exists()) {
                    String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
                    String backupFileName = dbFile.getName().replaceFirst("(\\.\\w+)?$", "_backup_" + timestamp + "$1");
                    File backupFile = new File(dbFile.getParent(), backupFileName);

                    java.nio.file.Files.copy(
                            dbFile.toPath(), backupFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    log.info("Backup created: " + backupFile.getAbsolutePath());
                }
            } catch (Exception ex) {
                log.info(ex.getMessage());
            }
        }

        int resp = JOptionPane.showConfirmDialog(
                null,
                "Are you sure you want to EXECUTE BACKUP DB (\"" + dataBaseType + "\")?",
                "Backup Database",
                JOptionPane.YES_NO_OPTION);

        if (resp == JOptionPane.YES_OPTION) {
            try (Connection conn = performDataBase.getConnection()) {
                performBackup.initialize(conn);

                String databasePath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
                ErrorMessage errorMessage;

                String backupFilePath = databasePath + File.separator + "backup_home_banking_" + date + ".sql";
                errorMessage = performBackup.backupHomeBanking(conn, backupFilePath);

                if (errorMessage == null) {
                    backupFilePath = databasePath + File.separator + "backup_home_url_" + date + ".sql";
                    errorMessage = performBackup.backupHomeUrl(conn, backupFilePath);
                }
                if (errorMessage == null) {
                    backupFilePath = databasePath + File.separator + "backup_bot_job_" + date + ".sql";
                    errorMessage = performBackup.backupBotJob(conn, backupFilePath);
                }
                if (errorMessage == null) {
                    backupFilePath = databasePath + File.separator + "backup_block_" + date + ".sql";
                    errorMessage = performBackup.backupBlock(conn, backupFilePath);
                }
                if (errorMessage == null) {
                    backupFilePath = databasePath + File.separator + "backup_instruction_" + date + ".sql";
                    errorMessage = performBackup.backupInstruction(conn, backupFilePath);
                }
                if (errorMessage == null) {
                    backupFilePath = databasePath + File.separator + "backup_variable_" + date + ".sql";
                    errorMessage = performBackup.backupVariable(conn, backupFilePath);
                }
                if (errorMessage == null) {
                    backupFilePath = databasePath + File.separator + "backup_reference_" + date + ".sql";
                    errorMessage = performBackup.backupReference(conn, backupFilePath);
                }
                if (errorMessage == null) {
                    backupFilePath = databasePath + File.separator + "backup_component_block_" + date + ".sql";
                    errorMessage = performBackup.backupComponentBlock(conn, backupFilePath);
                }
                if (errorMessage == null) {
                    backupFilePath = databasePath + File.separator + "backup_component_instruction_" + date + ".sql";
                    errorMessage = performBackup.backupComponentInstruction(conn, backupFilePath);
                }
                if (errorMessage == null) {
                    backupFilePath = databasePath + File.separator + "backup_component_variable_" + date + ".sql";
                    errorMessage = performBackup.backupComponentVariable(conn, backupFilePath);
                }
                if (errorMessage == null) {
                    backupFilePath = databasePath + File.separator + "backup_component_reference_" + date + ".sql";
                    errorMessage = performBackup.backupComponentReference(conn, backupFilePath);
                }

                if (errorMessage == null) {
                    JOptionPane.showMessageDialog(
                            null,
                            "Backup DB Success!\nCheck the LOGS folder!",
                            "Backup",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(
                            null,
                            "Backup Database error:\n" + errorMessage.getErrorMessage(),
                            "Backup error",
                            JOptionPane.ERROR_MESSAGE);
                }

            } catch (SQLException ex) {
                log.info(ex.getMessage());
            }
        }
    }

    private void runRestoreScripts() {
        String text = restoreDateField.getText();
        LocalDate selectedDate;
        try {
            selectedDate = LocalDate.parse(text, DateTimeFormatter.ISO_DATE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    null,
                    "Please enter a valid restore date in format yyyy-MM-dd.",
                    "Restore Warning",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        String formattedDate = selectedDate.format(DateTimeFormatter.ofPattern("yyyy_MM_dd"));

        String dataBaseType = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);
        String dataBaseFolder = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);

        if (dataBaseType.equalsIgnoreCase(((String) databaseChoiceBox.getSelectedItem()).trim())) {
            try {
                performDataBase.changeDbConnection();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        } else {
            performMessage.showCustomModalDialogDragWin11(
                    "Database Selection Mismatch ⚠️",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>The selected database type does not match the saved database!</span>",
                    "<span style='color: #1565C0; font-weight: bold;'>Please select the database that matches the saved type, or reload configurations to apply your selection.</span>",
                    "<span style='color: #6A1B9A; font-weight: bold;'>Selected Database:</span> "
                            + databaseChoiceBox.getSelectedItem(),
                    "<span style='color: #6A1B9A; font-weight: bold;'>Saved Database:</span> "
                            + dataBaseType + "<br/>"
                            + "<span style='color: #E65100; font-weight: bold;'>💡 Reminder:</span> Press the <span style='text-decoration: underline;'>Reload Configs</span> button to save and apply your database choice before continuing.",
                    false,
                    "OK",
                    null,
                    0);
            return;
        }

        int resp = JOptionPane.showConfirmDialog(
                null,
                "Are you sure you want to EXECUTE RESTORE DB (\"" + dataBaseType + "\")?",
                "Restore Database",
                JOptionPane.YES_NO_OPTION);

        if (resp != JOptionPane.YES_OPTION) {
            return;
        }

        // also show custom HTML-ish message via performMessage (kept)
        ARExecution.DialogModal respModal = performMessage.showCustomModalDialogDragWin11(
                "Restore Database Confirmation",
                "<span style='font-weight: bold; color: #D32F2F;'>Are you sure you want to execute a database restore?</span>",
                "The database type selected is: <span style='color: #1565C0; font-weight: bold;'>" + dataBaseType
                        + "</span>.",
                "<span style='color: #6A1B9A; font-weight: bold;'>The restore will apply to the folder: </span>.",
                "<span style='font-style: italic;'>Details: " + dataBaseFolder + "</span>",
                false,
                "Execute Restore",
                "Cancel",
                0);

        if (respModal.equals(ARExecution.DialogModal.STOP)) {
            return;
        }

        try (Connection conn = performDataBase.getConnection()) {
            performBackup.initialize(conn);

            String databasePath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
            ErrorMessage errorMessage;

            String backupFilePath = databasePath + File.separator + "backup_home_banking_" + formattedDate + ".sql";
            errorMessage = performBackup.restoreHomeBanking(conn, backupFilePath);

            if (errorMessage == null) {
                backupFilePath = databasePath + File.separator + "backup_home_url_" + formattedDate + ".sql";
                errorMessage = performBackup.restoreHomeUrl(conn, backupFilePath);
            }
            if (errorMessage == null) {
                backupFilePath = databasePath + File.separator + "backup_bot_job_" + formattedDate + ".sql";
                errorMessage = performBackup.restoreBotJob(conn, backupFilePath);
            }
            if (errorMessage == null) {
                backupFilePath = databasePath + File.separator + "backup_block_" + formattedDate + ".sql";
                errorMessage = performBackup.restoreBlock(conn, backupFilePath);
            }
            if (errorMessage == null) {
                backupFilePath = databasePath + File.separator + "backup_instruction_" + formattedDate + ".sql";
                errorMessage = performBackup.restoreInstruction(conn, backupFilePath);
            }
            if (errorMessage == null) {
                backupFilePath = databasePath + File.separator + "backup_variable_" + formattedDate + ".sql";
                errorMessage = performBackup.restoreVariable(conn, backupFilePath);
            }
            if (errorMessage == null) {
                errorMessage = performBackup.restoreUpdateInstruction(conn);
            }
            if (errorMessage == null) {
                backupFilePath = databasePath + File.separator + "backup_reference_" + formattedDate + ".sql";
                errorMessage = performBackup.restoreReference(conn, backupFilePath);
            }
            if (errorMessage == null) {
                backupFilePath = databasePath + File.separator + "backup_component_block_" + formattedDate + ".sql";
                errorMessage = performBackup.restoreComponentBlock(conn, backupFilePath);
            }
            if (errorMessage == null) {
                backupFilePath =
                        databasePath + File.separator + "backup_component_instruction_" + formattedDate + ".sql";
                errorMessage = performBackup.restoreComponentInstruction(conn, backupFilePath);
            }
            if (errorMessage == null) {
                backupFilePath = databasePath + File.separator + "backup_component_variable_" + formattedDate + ".sql";
                errorMessage = performBackup.restoreComponentVariable(conn, backupFilePath);
            }
            if (errorMessage == null) {
                errorMessage = performBackup.restoreComponentUpdateInstruction(conn);
            }
            if (errorMessage == null) {
                backupFilePath = databasePath + File.separator + "backup_component_reference_" + formattedDate + ".sql";
                errorMessage = performBackup.restoreComponentReference(conn, backupFilePath);
            }

            if (errorMessage == null) {
                closeAllScenes();
                performLists.clearAllLists();

                errorMessage = performDBEngine.loadHomeBanking(null);
                if (errorMessage == null) {
                    errorMessage = performDBEngine.loadHomeUrls(null);
                }
                if (errorMessage == null) {
                    errorMessage = performDataBase.loadQuickBotJobs();
                }

                if (errorMessage != null) {
                    performMessage.errorMessageOperationFailed(errorMessage);
                }

                if (viewBotJobListView != null) {
                    viewBotJobListView.setListData(
                            performLists.getQuickBotJobs().toArray(new BotJobLoadDTO[0]));
                }

                backupDBButton.setEnabled(!performLists.getListHomeBanking().isEmpty());
                homeBankingListView.setListData(
                        performLists.getListHomeBanking().toArray(new HomeBankingLoadDTO[0]));

                HomeBankingLoadDTO homeBank = performLists.getFirstHomeBanking();
                arNewHomeBankingScene.initialize(homeBank);

                performMessage.showCustomModalDialogDragWin11(
                        "Restore DB Success! ✅",
                        "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>Database restored successfully!</span>",
                        "<span style='color: #1565C0; font-weight: bold;'>Now you can start to use your database!</span>",
                        "<span style='color: #6A1B9A; font-weight: bold;'>Database:</span> "
                                + databaseChoiceBox.getSelectedItem(),
                        "<span style='color: #E65100; font-weight: bold;'>💡 Don't forget:</span> Press the <span style='text-decoration: underline;'>Reload DB</span> button to refresh your data!",
                        false,
                        "OK",
                        null,
                        0);
            } else {
                log.error("Restore Database error: " + errorMessage.getErrorMessage());
                performMessage.errorMessage(
                        "Restore Database error",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>"
                                + errorMessage.getErrorTitle() + "</span> ❌",
                        "<span style='color: #E65100; font-weight: bold;'>Error Type:</span> "
                                + errorMessage.getErrorHeader(),
                        "<span style='font-style: italic;'>Detail:</span> " + errorMessage.getErrorMessage(),
                        null,
                        0);
            }
        } catch (SQLException error) {
            log.error("Restore Database error: " + error.getMessage());
        }
    }

    private void saveConfigurations() throws SQLException {
        boolean validfields = true;

        if (Strings.isNullOrEmpty(pathLicense.getText())) {
            JOptionPane.showMessageDialog(
                    null, "License Path must be filled!", "Field Blank", JOptionPane.ERROR_MESSAGE);
            validfields = false;
        }
        if (Strings.isNullOrEmpty(pathExcel.getText())) {
            JOptionPane.showMessageDialog(null, "Excel Path must be filled!", "Field Blank", JOptionPane.ERROR_MESSAGE);
            validfields = false;
        }
        if (Strings.isNullOrEmpty(pathLog.getText())) {
            JOptionPane.showMessageDialog(null, "Log Path must be filled!", "Field Blank", JOptionPane.ERROR_MESSAGE);
            validfields = false;
        }
        if (Strings.isNullOrEmpty(dbUrl.getText())) {
            JOptionPane.showMessageDialog(null, "Java Path must be filled!", "Field Blank", JOptionPane.ERROR_MESSAGE);
            validfields = false;
        }
        if (Strings.isNullOrEmpty(pathAccessDB.getText())) {
            JOptionPane.showMessageDialog(
                    null, "Database Path must be filled!", "Field Blank", JOptionPane.ERROR_MESSAGE);
            validfields = false;
        }
        if (Strings.isNullOrEmpty(pathReport.getText())) {
            JOptionPane.showMessageDialog(
                    null, "Reports Path must be filled!", "Field Blank", JOptionPane.ERROR_MESSAGE);
            validfields = false;
        }
        if (Strings.isNullOrEmpty(pathPriority.getText())) {
            JOptionPane.showMessageDialog(
                    null, "Priority Path must be filled!", "Field Blank", JOptionPane.ERROR_MESSAGE);
            validfields = false;
        }
        if (Strings.isNullOrEmpty(dbUser.getText())) {
            JOptionPane.showMessageDialog(
                    null, "Database \"User\" must be filled!", "Field Blank", JOptionPane.ERROR_MESSAGE);
            validfields = false;
        }
        if (Strings.isNullOrEmpty(dbPwd.getText())) {
            JOptionPane.showMessageDialog(
                    null, "Database \"Password\" must be filled!", "Field Blank", JOptionPane.ERROR_MESSAGE);
            validfields = false;
        }
        if (Strings.isNullOrEmpty(pathEngine.getText())) {
            JOptionPane.showMessageDialog(
                    null, "AR Engine Path must be filled!", "Field Blank", JOptionPane.ERROR_MESSAGE);
            validfields = false;
        }
        if (Strings.isNullOrEmpty(pathWebDriver.getText())) {
            JOptionPane.showMessageDialog(
                    null, "Web Driver Path must be filled!", "Field Blank", JOptionPane.ERROR_MESSAGE);
            validfields = false;
        }
        if (Strings.isNullOrEmpty(pathAppium.getText())) {
            JOptionPane.showMessageDialog(
                    null, "Appium Path must be filled!", "Field Blank", JOptionPane.ERROR_MESSAGE);
            validfields = false;
        }

        if (!validfields) {
            return;
        }

        arPropertyManager.setProperty(ARPropertyEnum.BROWSER.getValue(), (String) browserChoiceBox.getSelectedItem());
        arPropertyManager.setProperty(
                ARPropertyEnum.PATH_LICENSE.getValue(), pathLicense.getText().trim());
        arPropertyManager.setProperty(
                ARPropertyEnum.PATH_EXCEL.getValue(), pathExcel.getText().trim());
        arPropertyManager.setProperty(
                ARPropertyEnum.PATH_LOG.getValue(), pathLog.getText().trim());
        arPropertyManager.setProperty(
                ARPropertyEnum.PATH_PRIORITY.getValue(), pathPriority.getText().trim());
        arPropertyManager.setProperty(
                ARPropertyEnum.PATH_REPORT.getValue(), pathReport.getText().trim());
        arPropertyManager.setProperty(
                ARPropertyEnum.PATH_ENGINE.getValue(), pathEngine.getText().trim());
        arPropertyManager.setProperty(
                ARPropertyEnum.PATH_WEBDRIVER.getValue(),
                pathWebDriver.getText().trim());
        arPropertyManager.setProperty(
                ARPropertyEnum.PATH_APPIUM.getValue(), pathAppium.getText().trim());

        try {
            performInitializer.testConnection(
                    (String) databaseChoiceBox.getSelectedItem(),
                    pathAccessDB.getText().trim(),
                    dbUrl.getText().trim(),
                    dbUser.getText().trim(),
                    dbPwd.getText().trim());
        } catch (Exception error) {
            log.error("testConnection Error: " + error.getMessage());
            performMessage.errorMessage(
                    "Database connection Failed",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>An error occurred during the Database connection.</span>",
                    "<span style='font-weight: bold;'>" + databaseChoiceBox.getSelectedItem() + "</span>.",
                    "<span style='color: #E65100; font-weight: bold;'>Please ensure the Database connections are correct.</span>",
                    "<span style='font-style: italic;'>Details: " + error.getMessage() + "</span>",
                    0);
            return;
        }

        arPropertyManager.setProperty(
                ARPropertyEnum.DATABASE_TYPE.getValue(), (String) databaseChoiceBox.getSelectedItem());
        arPropertyManager.setProperty(
                ARPropertyEnum.PATH_DB.getValue(), pathAccessDB.getText().trim());
        arPropertyManager.setProperty(
                ARPropertyEnum.DB_URL.getValue(), dbUrl.getText().trim());
        arPropertyManager.setProperty(
                ARPropertyEnum.DB_USER.getValue(), dbUser.getText().trim());
        arPropertyManager.setProperty(
                ARPropertyEnum.DB_PWD.getValue(), dbPwd.getText().trim());

        performDataBase.changeDbConnection();
        closeAllScenes();
        performLists.clearAllLists();

        ErrorMessage errorMessage = performDBEngine.loadHomeBanking(null);
        if (errorMessage == null) {
            errorMessage = performDBEngine.loadHomeUrls(null);
        }
        if (errorMessage == null) {
            errorMessage = performDataBase.loadQuickBotJobs();
        }
        if (errorMessage != null) {
            performMessage.errorMessageOperationFailed(errorMessage);
        }

        if (viewBotJobListView != null) {
            viewBotJobListView.setListData(performLists.getQuickBotJobs().toArray(new BotJobLoadDTO[0]));
        }

        backupDBButton.setEnabled(!performLists.getListHomeBanking().isEmpty());
        homeBankingListView.setListData(performLists.getListHomeBanking().toArray(new HomeBankingLoadDTO[0]));

        HomeBankingLoadDTO homeBank = performLists.getFirstHomeBanking();
        arNewHomeBankingScene.initialize(homeBank);

        if (!this.previousDB.equalsIgnoreCase((String) databaseChoiceBox.getSelectedItem())
                || !this.previousDBUrl.equalsIgnoreCase(dbUrl.getText().trim())) {

            errorMessage = performDataBase.loadQuickBotJobs();
            if (errorMessage != null) {
                performMessage.errorMessageOperationFailed(errorMessage);
            }
            this.previousDB = (String) databaseChoiceBox.getSelectedItem();
            this.previousDBUrl = dbUrl.getText().trim();
        }

        JOptionPane.showMessageDialog(
                null,
                "The configuration has been saved and the data has been reloaded",
                "Configuration saved",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void deleteAllDB() {
        if (isEnabledLicence && !checkLicense()) {
            return;
        }

        String dataBaseType = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);

        if (dataBaseType.equalsIgnoreCase(((String) databaseChoiceBox.getSelectedItem()).trim())) {
            try {
                performDataBase.changeDbConnection();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        } else {
            performMessage.showCustomModalDialogDragWin11(
                    "Database Selection Mismatch ⚠️",
                    "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>The selected database type does not match the saved database!</span>",
                    "<span style='color: #1565C0; font-weight: bold;'>Please select the database that matches the saved type, or reload configurations to apply your selection.</span>",
                    "<span style='color: #6A1B9A; font-weight: bold;'>Selected Database:</span> "
                            + databaseChoiceBox.getSelectedItem(),
                    "<span style='color: #6A1B9A; font-weight: bold;'>Saved Database:</span> "
                            + dataBaseType + "<br/>"
                            + "<span style='color: #E65100; font-weight: bold;'>💡 Reminder:</span> Press the <span style='text-decoration: underline;'>Reload Configs</span> button to save and apply your database choice before continuing.",
                    false,
                    "OK",
                    null,
                    0);
            return;
        }

        int resp = JOptionPane.showConfirmDialog(
                null,
                "Are you sure you want to DELETE ALL JOB TABLES ROWS (\"" + dataBaseType + "\")?",
                "Delete ALL DB",
                JOptionPane.YES_NO_OPTION);

        if (resp == JOptionPane.YES_OPTION) {
            if (performDataBase.deleteAllJobDetails(dataBaseType)) {
                JOptionPane.showMessageDialog(
                        null,
                        "All Job Details have been deleted and data reloaded.",
                        "Delete All",
                        JOptionPane.INFORMATION_MESSAGE);
            } else {
                String msg = "\"" + dataBaseType + "\" Problems!\n"
                        + "The Instructions and Job Details cannot be deleted and the data has been reloaded\n";
                if (dataBaseType != null && dataBaseType.equalsIgnoreCase("ACCESS")) {
                    msg += dataBaseType + " database Recommendation:\nDelete entire \"database.mdb\" file!";
                }
                JOptionPane.showMessageDialog(null, msg, "Delete All Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private JTextField createPathTextField(ARPropertyEnum property) {
        JTextField textField = new JTextField();
        textField.setText(arPropertyManager.getProperty(property));
        return textField;
    }

    private JButton createPathButton() {
        // Assuming ARComponentBuilder is now Swing-aware and returns JButton
        JButton button = builder.buildButton("...");
        return button;
    }

    private void openChooserFor(JTextField field, boolean isDirectory) {
        String folderBase = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
        if (Strings.isNullOrEmpty(folderBase)) {
            folderBase = System.getProperty("user.dir");
        }

        File startingPoint = new File(folderBase);
        String chosenPath = isDirectory ? openDirectoryChooserFor(startingPoint) : openFileChooserFor(startingPoint);
        if (!Strings.isNullOrEmpty(chosenPath)) {
            field.setText(chosenPath);
        }
    }

    private String openDirectoryChooserFor(File startingDirectory) {
        JFileChooser chooser = new JFileChooser(startingDirectory);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = chooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            File chosenPath = chooser.getSelectedFile();
            return chosenPath.getAbsolutePath();
        }
        return null;
    }

    private String openFileChooserFor(File startingDirectory) {
        JFileChooser chooser = new JFileChooser(startingDirectory);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int result = chooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            File chosenPath = chooser.getSelectedFile();
            return chosenPath.getAbsolutePath();
        }
        return null;
    }

    private boolean checkLicense() {
        try {
            String licensePath = arPropertyManager.getProperty(ARPropertyEnum.PATH_LICENSE);
            if (Strings.isNullOrEmpty(licensePath)) {
                licensePath = System.getProperty("user.dir");
            }

            LicenceVal licenseStatus = LicenseManager.checkLicenseFile(licensePath);

            String msgValid = "The license file is valid and the application is authorized for use.";
            String msgNextStep = "You can now proceed with normal application usage.";
            String msgColor = "#0277BD";

            if (!licenseStatus.equals(LicenceVal.VALID)) {
                msgValid = "The license file is not valid and the application is not authorized for use.";
                msgNextStep = "Application access is restricted. Please obtain a valid license to continue.";
                msgColor = "#C62828";

                performMessage.showCustomModalDialogDragWin11(
                        "License Status Verification",
                        "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>License status has been successfully verified.</span>",
                        "<span style='color: " + msgColor + "; font-weight: bold;'>" + msgValid + "</span>",
                        "<span style='font-style: italic;'>" + msgNextStep + "</span>",
                        "<span style='color: #E65100; font-weight: bold;'>Current license status:</span> <span style='font-weight: bold;'>"
                                + licenseStatus.getStaus() + "</span>",
                        false,
                        "OK",
                        null,
                        0);
                return false;
            }
            return true;
        } catch (Exception error) {
            log.error("Cannot read/validate the License path/file. Error: " + error.getMessage());
            return false;
        }
    }

    private void closeAllScenes() {
        if (arNewBotJobScene != null) {
            arNewBotJobScene.closeModal();
        }
        //        if (arNewCommandScene != null) {
        //            arNewCommandScene.setSplitDTO(null);
        //            arNewCommandScene.closeModal();
        //        }
        if (arElementValueScene != null) {
            arElementValueScene.setSplitDTO(null);
            arElementValueScene.closeModal();
        }
        if (arViewBotJobScene != null) {
            arViewBotJobScene.closeModal();
        }
        if (arNewHomeBankingScene != null) {
            arNewHomeBankingScene.closeModal();
        }
        //        if (arScannedElementScene != null) {
        //            arScannedElementScene.closeModal();
        //            arScannedElementScene.closeWebDrivers();
        //        }
        if (arWebDriver != null) {
            arWebDriver.closeAllDrivers();
            arWebDriver.closeCurrentDriver();
        }
    }
}
