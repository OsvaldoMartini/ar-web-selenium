package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.listCell.ARCellFactory;
import com.allinweb.ch.component.listCell.HomeBankingListCell;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.model.HomeBankingLoadDTO;
import com.allinweb.ch.component.model.InstructionLoadDTO;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.ARAlertScene;
import com.allinweb.ch.component.scene.ARElementValueScene;
import com.allinweb.ch.component.scene.ARNewBotJobScene;
import com.allinweb.ch.component.scene.ARNewCommandScene;
import com.allinweb.ch.component.scene.ARNewHomeBankingScene;
import com.allinweb.ch.component.scene.ARScannedElementScene;
import com.allinweb.ch.component.scene.ARViewBotJobScene;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.facade.PerformBackup;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformInitializer;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.license.LicenceVal;
import com.allinweb.ch.license.LicenseManager;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import com.allinweb.ch.util.ErrorMessage;
import com.google.common.base.Strings;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ARConfigurationPane extends ARPane {

    protected static volatile ARConfigurationPane instance;

    // Private constructor to prevent instantiation
    private ARConfigurationPane() {
        // Initialize if necessary
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

    private ListView<BotJobLoadDTO> viewBotJobListView;
    private ObservableList<BotJobLoadDTO> botJobList;
    private Stage modalStage;

    private boolean isEnabledLicence;

    public void initialize(
            Stage modalStage,
            ListView<BotJobLoadDTO> viewBotJobListView,
            ObservableList<BotJobLoadDTO> botJobList,
            boolean isEnabledLicence) {
        this.isEnabledLicence = isEnabledLicence;
        this.modalStage = modalStage;
        this.viewBotJobListView = viewBotJobListView;
        this.botJobList = botJobList;
    }

    private static final ARComponentBuilder builder = new ARComponentBuilder();

    private static final int SECONDS = 3; // Total seconds for the countdown
    private int remainingSeconds = SECONDS;
    private Timeline timeline;
    private ExecutorService executorService;
    private Alert alertToShow;

    private static final ARPropertyManager arPropertyManager;
    private static final ARNewHomeBankingScene arNewHomeBankingScene;
    private static final PerformMessage performMessage;
    private static final PerformDataBase performDataBase;
    private static final PerformBackup performBackup;
    private static final PerformInitializer performInitializer;

    private static final ARScannedElementScene arScannedElementScene;
    private static final ARViewBotJobScene arViewBotJobScene;
    private static final ARNewCommandScene arNewCommandScene;
    private static final ARElementValueScene arElementValueScene;
    private static final ARNewBotJobScene arNewBotJobScene;

    // Static block to initialize
    static {
        arScannedElementScene = ARScannedElementScene.getInstance();
        arNewCommandScene = ARNewCommandScene.getInstance();
        arElementValueScene = ARElementValueScene.getInstance();
        arViewBotJobScene = ARViewBotJobScene.getInstance();
        arNewBotJobScene = ARNewBotJobScene.getInstance();

        arPropertyManager = ARPropertyManager.getInstance();
        performMessage = PerformMessage.getInstance();
        performDataBase = PerformDataBase.getInstance();
        arNewHomeBankingScene = ARNewHomeBankingScene.getInstance();
        performBackup = PerformBackup.getInstance();
        performInitializer = PerformInitializer.getInstance();
    }

    private ObservableList<HomeBankingLoadDTO> homeBankingList = FXCollections.observableArrayList();
    private ListView<HomeBankingLoadDTO> homeBankingListView;

    // UI Components
    Label title;
    Label pathExcelLabel;
    Label pathLicenseLabel;
    //    Label pathExportLabel;
    //    Label fileExportLabel;
    Label pathLogLabel;
    Label sizeLogLabel;
    Label reduceSearchLabel;

    Label dbUrlLabel;
    Label dbUserLabel;
    Label dbPwdLabel;

    Label pathAccessDBLabel;
    Label databaseLabel;

    Label pathReportLabel;
    Label pathPriorityLabel;
    Label pathEngineLabel;
    Label browserLabel;
    Label reloadDBLabel;
    Label backupDBLabel;
    Label deleteAllDBLabel;
    Label insertSitesLabel;
    Label pathWebDriverLabel;

    TextField pathExcel;
    TextField pathLicense;
    TextField pathLog;
    TextField pathAccessDB;
    TextField pathReport;
    TextField pathPriority;

    TextField dbUrl;
    TextField dbUser;
    TextField dbPwd;

    TextField pathEngine;
    TextField pathWebDriver;

    ChoiceBox<String> browserChoiceBox = new ChoiceBox<>();
    ChoiceBox<String> databaseChoiceBox = new ChoiceBox<>();
    ObservableList<String> browserList =
            FXCollections.observableArrayList(ARConstants.CHROME, ARConstants.EDGE, ARConstants.FIREFOX);

    ObservableList<String> databaseList = FXCollections.observableArrayList(
            ARConstants.ACCESS, ARConstants.POSTGRES, ARConstants.SQLSERVER, ARConstants.SQLITE);

    Button pathExcelButton;
    Button pathLicenseButton;
    //    Button pathExportButton;
    Button pathLogButton;

    Button pathAccessDBButton;
    Button pathReportButton;
    Button pathPriorityButton;

    Button pathEngineButton;
    Button pathWebDriverButton;

    Button reloadDBButton;
    Button backupDBButton;
    Button restoreDBButton;
    Button deleteAllDBButton;
    Button addHomeBankingButton;

    VBox pathGroup;

    AnchorPane mainPane;

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {
        //  Alert Timer Components
        // Create a label to display the countdown
        Label countdownLabel = new Label(String.valueOf(remainingSeconds));
        countdownLabel.setStyle("-fx-font-size: 24px;");
        countdownLabel.setVisible(false);
        // Create a stack pane to hold the label
        StackPane stackPane = new StackPane(countdownLabel);
        stackPane.setPadding(new Insets(20));

        // Create a dialog for the alert
        alertToShow = new Alert(Alert.AlertType.INFORMATION);
        alertToShow.setTitle("Title");
        alertToShow.setHeaderText("Header Message");
        alertToShow.setContentText("Main Message");
        alertToShow.initModality(Modality.WINDOW_MODAL);
        // Set the content of the alert
        alertToShow.getDialogPane().setContent(stackPane);
        // Create a timeline to update the countdown
        timeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), event -> {
            remainingSeconds--;
            countdownLabel.setText(String.valueOf(remainingSeconds));
            if (remainingSeconds <= 0) {
                timeline.stop(); // Stop the timeline when countdown finishes
                alertToShow.close(); // Close the alert dialog
            }
        }));

        title = new Label("Configuration");
        title.setMaxHeight(ARConstants.SPACE_L);
        title.setBackground(new Background(
                new BackgroundFill(Color.ROYALBLUE, new CornerRadii(ARConstants.SPACE_XS), Insets.EMPTY)));
        title.setTextFill(Color.WHITE);
        AnchorPane.setTopAnchor(title, ARConstants.SPACE_M);
        AnchorPane.setLeftAnchor(title, ARConstants.SPACE_M);
        AnchorPane.setRightAnchor(title, ARConstants.SPACE_M);

        //        ButtonBar homeBankingActionGroup = new ButtonBar();
        addHomeBankingButton = builder.buildButton("Insert Organizations");
        //        homeBankingActionGroup.getButtons().addAll(addHomeBankingButton);

        //        ObservableList<HomeBankingDTO> homeBankingList =
        //                PerformDataBase..getEntityList(HomeBankingDTO.class);

        if (performDataBase.getConn() != null) {
            homeBankingList.addAll(performDataBase.loadHomeBanking(null));
        }
        homeBankingListView = new ListView<>(homeBankingList);
        homeBankingListView.setCellFactory(new ARCellFactory<>(HomeBankingListCell.class)::call);

        // Setting the preferred height for homeBankingListView
        homeBankingListView.setPrefHeight(100); // Set the height to 50px

        // Add homeBankingListView to a VBox if needed (optional, not mandatory for height adjustment)
        VBox homeBankingContainer = new VBox(homeBankingListView);
        //        homeBankingContainer.setSpacing(2); // O

        pathLicenseLabel = new Label("License Path:");
        pathLicense = createPathTextField(ARPropertyEnum.PATH_LICENSE);
        pathLicenseButton = createPathButton();
        AnchorPane licenseGroup = new AnchorPane(pathLicense, pathLicenseButton);

        pathExcelLabel = new Label("Excel Path:");
        pathExcel = createPathTextField(ARPropertyEnum.PATH_EXCEL);
        pathExcelButton = createPathButton();
        AnchorPane excelGroup = new AnchorPane(pathExcel, pathExcelButton);

        // LOGs
        pathLogLabel = new Label("Log Path:");
        pathLog = createPathTextField(ARPropertyEnum.PATH_LOG);
        pathLogButton = createPathButton();

        GridPane gridPaneLog = new GridPane();
        //        gridPaneLog.setVgap(10);
        gridPaneLog.setHgap(10);
        // Set column constraints for pathLog (80%), sizeLog (15%), and pathLogButton (5%)
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(95);

        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(5);
        gridPaneLog.getColumnConstraints().addAll(col1, col2);

        // Add labels in the first row
        gridPaneLog.add(pathLogLabel, 0, 0);

        // Add text fields in the second row
        gridPaneLog.add(pathLog, 0, 1);

        // Add button in the second row, third column
        gridPaneLog.add(pathLogButton, 1, 1);

        // Set margin for pathLogButton to create spacing from right border
        GridPane.setMargin(pathLogButton, new Insets(0, 0, 0, 5));

        // DB Type
        pathAccessDBLabel = new Label("Access Database Path:");
        pathAccessDB = createPathTextField(ARPropertyEnum.PATH_DB);
        pathAccessDBButton = createPathButton();

        GridPane gridPaneDB = new GridPane();
        gridPaneDB.setHgap(10);

        // Set column constraints for pathDB (80%), dbType (15%), socketPort (15%) and pathDBButton (5%)
        ColumnConstraints col1DB = new ColumnConstraints();
        col1DB.setPercentWidth(95);

        ColumnConstraints col2DB = new ColumnConstraints();
        col2DB.setPercentWidth(5);
        //
        //        ColumnConstraints col3DB = new ColumnConstraints();
        //        col3DB.setPercentWidth(5);

        gridPaneDB.getColumnConstraints().addAll(col1DB, col2DB);

        // Add labels in the first row
        gridPaneDB.add(pathAccessDBLabel, 0, 0);
        //        gridPaneDB.add(socketPortLabel, 1, 0);
        //        gridPaneDB.add(blockLimitLabel, 2, 0);

        // Add text fields in the second row
        gridPaneDB.add(pathAccessDB, 0, 1);
        //        gridPaneDB.add(databaseChoiceBox, 1, 1);
        //        gridPaneDB.add(socketPort, 1, 1);
        //        gridPaneDB.add(blockLimit, 2, 1);

        // Add button in the second row, third column
        gridPaneDB.add(pathAccessDBButton, 1, 1);

        // Set margin for pathDBButton to create spacing from right border
        GridPane.setMargin(pathAccessDBButton, new Insets(0, 0, 0, 5));

        GridPane gridPaneButton = new GridPane();
        gridPaneButton.setHgap(2);

        // Set column constraints for each column to take up 33.33% of the grid width
        ColumnConstraints col1Button = new ColumnConstraints();
        col1Button.setPercentWidth(16);

        ColumnConstraints col2Button = new ColumnConstraints();
        col2Button.setPercentWidth(16);

        ColumnConstraints col3Button = new ColumnConstraints();
        col3Button.setPercentWidth(16);

        ColumnConstraints col4Button = new ColumnConstraints();
        col4Button.setPercentWidth(16);

        ColumnConstraints col5Button = new ColumnConstraints();
        col5Button.setPercentWidth(16);

        ColumnConstraints col6Button = new ColumnConstraints();
        col6Button.setPercentWidth(16);

        gridPaneButton
                .getColumnConstraints()
                .addAll(col1Button, col2Button, col3Button, col4Button, col5Button, col6Button);

        browserLabel = new Label("Browser");
        databaseLabel = new Label("DB Type");

        backupDBLabel = new Label("Backup DB");

        reloadDBLabel = new Label("Reload DB");
        deleteAllDBLabel = new Label("Delete ALL DB");
        insertSitesLabel = new Label("Insert Sites");

        backupDBButton = builder.buildButton("Backup");
        backupDBButton.setMaxHeight(ARConstants.SPACE_XXS);

        restoreDBButton = builder.buildButton("Restore");
        restoreDBButton.setMaxHeight(ARConstants.SPACE_XXS);

        backupDBLabel.setVisible(true);
        backupDBButton.setVisible(true);
        restoreDBButton.setVisible(true);

        HBox backupRestoreGroup = new HBox(0); // spacing = 0 to eliminate gap
        backupRestoreGroup.setAlignment(Pos.CENTER_LEFT); // or CENTER if you want them centered
        // Optional: remove any padding if previously set
        backupRestoreGroup.setPadding(Insets.EMPTY);
        backupRestoreGroup.getChildren().addAll(backupDBButton, restoreDBButton);

        reloadDBButton = builder.buildButton("Reload Configs");
        reloadDBButton.setMaxHeight(ARConstants.SPACE_L);

        deleteAllDBButton = builder.buildButton("Delete DB");
        deleteAllDBButton.setMaxHeight(ARConstants.SPACE_L);
        deleteAllDBButton.setStyle("-fx-background-color: lightcoral; -fx-text-fill: blue;");

        browserChoiceBox.setItems(browserList);
        databaseChoiceBox.setItems(databaseList);
        databaseChoiceBox.setDisable(false);

        // Add labels in the first row
        gridPaneButton.add(browserLabel, 0, 0);
        gridPaneButton.add(databaseLabel, 1, 0);
        gridPaneButton.add(reloadDBLabel, 2, 0);
        gridPaneButton.add(backupDBLabel, 3, 0);
        gridPaneButton.add(deleteAllDBLabel, 4, 0);
        gridPaneButton.add(insertSitesLabel, 5, 0);

        // Add components in the second row, each occupying 25% of the width
        gridPaneButton.add(browserChoiceBox, 0, 1);
        gridPaneButton.add(databaseChoiceBox, 1, 1);
        gridPaneButton.add(reloadDBButton, 2, 1);
        gridPaneButton.add(backupRestoreGroup, 3, 1);
        gridPaneButton.add(deleteAllDBButton, 4, 1);
        gridPaneButton.add(addHomeBankingButton, 5, 1);

        //        AnchorPane logGroup = new AnchorPane(pathLog, sizeLog, pathLogButton);
        dbUrlLabel = new Label("Database URL:");
        dbUrl = createPathTextField(ARPropertyEnum.DB_URL);

        pathReportLabel = new Label("Report Path:");
        pathReport = createPathTextField(ARPropertyEnum.PATH_REPORT);
        pathReportButton = createPathButton();
        AnchorPane reportGroup = new AnchorPane(pathReport, pathReportButton);

        pathPriorityLabel = new Label("Priority Path:");
        pathPriority = createPathTextField(ARPropertyEnum.PATH_PRIORITY);
        pathPriorityButton = createPathButton();
        AnchorPane priorityGroup = new AnchorPane(pathPriority, pathPriorityButton);

        dbUserLabel = new Label("Database User)");
        dbPwdLabel = new Label("Database Password)");
        dbUser = createPathTextField(ARPropertyEnum.DB_USER);
        dbPwd = createPathTextField(ARPropertyEnum.DB_PWD);
        //        AnchorPane dbUserPwdGroup = new AnchorPane(dbUser, dbPwd);

        GridPane dbUserPwdGroup = new GridPane();
        dbUserPwdGroup.setHgap(10);
        dbUserPwdGroup.setVgap(5);

        ColumnConstraints colDbUser = new ColumnConstraints();
        colDbUser.setPercentWidth(50);
        ColumnConstraints colDbPwd = new ColumnConstraints();
        colDbPwd.setPercentWidth(50);
        dbUserPwdGroup.getColumnConstraints().addAll(colDbUser, colDbPwd);

        dbUserPwdGroup.add(dbUserLabel, 0, 0);
        dbUserPwdGroup.add(dbPwdLabel, 1, 0);
        dbUserPwdGroup.add(dbUser, 0, 1);
        dbUserPwdGroup.add(dbPwd, 1, 1);

        pathEngineLabel = new Label("Engine Path:");
        pathEngine = createPathTextField(ARPropertyEnum.PATH_ENGINE);
        pathEngineButton = createPathButton();
        AnchorPane engineGroup = new AnchorPane(pathEngine, pathEngineButton);

        pathWebDriverLabel = new Label("Web Driver Path:");
        pathWebDriver = createPathTextField(ARPropertyEnum.PATH_WEBDRIVER);
        pathWebDriverButton = createPathButton();
        AnchorPane driverGroup = new AnchorPane(pathWebDriver, pathWebDriverButton);

        Label organizationsLabel = new Label("Organizations");
        organizationsLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1565C0;");
        organizationsLabel.setAlignment(Pos.CENTER);
        organizationsLabel.setMaxWidth(Double.MAX_VALUE);

        pathGroup = new VBox(
                pathLicenseLabel,
                licenseGroup,
                pathExcelLabel,
                excelGroup,
                //                gridPaneExport,
                gridPaneLog,
                gridPaneDB,
                pathReportLabel,
                reportGroup,
                pathPriorityLabel,
                priorityGroup,
                pathEngineLabel,
                engineGroup,
                pathWebDriverLabel,
                driverGroup,
                dbUrlLabel,
                dbUrl,
                dbUserPwdGroup,
                gridPaneButton,
                organizationsLabel,
                homeBankingContainer);

        VBox.setVgrow(homeBankingContainer, Priority.ALWAYS);
        homeBankingContainer.setMaxHeight(Double.MAX_VALUE);

        VBox.setVgrow(homeBankingListView, Priority.ALWAYS);
        homeBankingListView.setMaxHeight(Double.MAX_VALUE);

        AnchorPane.setTopAnchor(pathGroup, ARConstants.SPACE_L + ARConstants.SPACE_M);
        AnchorPane.setLeftAnchor(pathGroup, ARConstants.SPACE_M);
        AnchorPane.setRightAnchor(pathGroup, ARConstants.SPACE_M);
        AnchorPane.setBottomAnchor(pathGroup, ARConstants.SPACE_M); // Allow bottom expansion

        mainPane = new AnchorPane(title, pathGroup);
    }

    @Override
    public void initUIBehaviour() {

        // Add listener to databaseChoiceBox
        databaseChoiceBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (ARConstants.ACCESS.equals(newVal)) {
                pathAccessDB.setDisable(false);
                dbUrl.setDisable(true);
                dbUser.setDisable(true);
                dbPwd.setDisable(true);
            } else {
                pathAccessDB.setDisable(true);
                dbUrl.setDisable(false);
                dbUser.setDisable(false);
                dbPwd.setDisable(false);
            }
        });

        if (performDataBase.getConn() != null) {
            try (Connection conn = performDataBase.getConnection()) {
                List<BotJobLoadDTO> botJobLoadList = performDataBase.loadAllBotJobs(conn);

                List<InstructionLoadDTO> instList = null;

                if ((instList != null && instList.size() > 0) || botJobLoadList.size() == 0) {
                    backupDBLabel.setVisible(false);
                    backupDBButton.setVisible(false);
                }
            } catch (SQLException ignore) {
                System.out.println("Check if It Was Migrated! - Not Migrate Columns found!");
            }
        }

        addHomeBankingButton.setOnMouseClicked(e -> {
            if (isEnabledLicence && !checkLicense()) {
                return;
            }
            arNewHomeBankingScene.initialize(homeBankingList);
            Stage currentStage = (Stage) addHomeBankingButton.getScene().getWindow();
            arNewHomeBankingScene.showModal(currentStage);
        });

        pathLicenseButton.setOnMouseClicked(e -> openChooserFor(pathLicense, modalStage, true));
        pathExcelButton.setOnMouseClicked(e -> openChooserFor(pathExcel, modalStage, true));
        pathLogButton.setOnMouseClicked(e -> openChooserFor(pathLog, modalStage, true));
        pathAccessDBButton.setOnMouseClicked(e -> openChooserFor(pathAccessDB, modalStage, true));
        pathReportButton.setOnMouseClicked(e -> openChooserFor(pathReport, modalStage, true));
        pathPriorityButton.setOnMouseClicked(e -> openChooserFor(pathPriority, modalStage, true));

        pathEngineButton.setOnMouseClicked(e -> openChooserFor(pathEngine, modalStage, false));
        pathWebDriverButton.setOnMouseClicked(e -> openChooserFor(pathWebDriver, modalStage, false));

        browserChoiceBox.setValue(arPropertyManager.getProperty(ARPropertyEnum.BROWSER));

        if (arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE) == null) {
            databaseChoiceBox.setValue("Access");
        } else {
            databaseChoiceBox.setValue(arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE));
        }

        reloadDBButton.setOnMouseClicked(e -> {
            try {
                saveConfigurations();
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        });
        backupDBButton.setOnMouseClicked(e -> runBackupScripts());
        restoreDBButton.setOnMouseClicked(e -> runrRestoreScripts());
        deleteAllDBButton.setOnMouseClicked(e -> deleteAllDB());
    }

    private void runBackupScripts() {
        String dataBaseType = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"));

        if (dataBaseType.equalsIgnoreCase("ACCESS")) {
            String dbPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
            File dbFile = new File(dbPath + ARConstants.FILE_NAME_ACCESS);

            try {
                if (dbFile.exists()) {
                    // Format the current date and time: yyyy_MM_dd_HH_mm_ss
                    String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());

                    // Create backup filename with timestamp before extension
                    String backupFileName = dbFile.getName().replaceFirst("(\\.\\w+)?$", "_backup_" + timestamp + "$1");

                    File backupFile = new File(dbFile.getParent(), backupFileName);

                    try {
                        java.nio.file.Files.copy(
                                dbFile.toPath(),
                                backupFile.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("Backup created: " + backupFile.getAbsolutePath());
                    } catch (java.io.IOException e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }

        Label newInstruction =
                new Label("DB BACKUP\nDatabase Selected: \"" + dataBaseType + "\" \nLog Folder : \"v4.1f Beta Test\"");
        newInstruction.setStyle("-fx-font-size: 18px; -fx-text-fill: red;");

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, null, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText("Are you sure you want to EXECUTE BACKUP DB (\"" + dataBaseType + "\")?");
        alert.getDialogPane().setContent(newInstruction);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {

            try (Connection conn = performDataBase.getConnection()) {

                performBackup.initialize(conn);

                String logPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_LOG);

                String backupFilePath = logPath + File.separator + "backup_home_banking_" + date + ".sql";
                ErrorMessage errorMessage = performBackup.backupHomeBanking(conn, backupFilePath);

                if (errorMessage == null) {
                    backupFilePath = logPath + File.separator + "backup_home_url_" + date + ".sql";
                    errorMessage = performBackup.backupHomeUrl(conn, backupFilePath);
                }

                if (errorMessage == null) {
                    backupFilePath = logPath + File.separator + "backup_bot_job_" + date + ".sql";
                    errorMessage = performBackup.backupBotJob(conn, backupFilePath);
                }

                if (errorMessage == null) {
                    backupFilePath = logPath + File.separator + "backup_block_" + date + ".sql";
                    errorMessage = performBackup.backupBlock(conn, backupFilePath);
                }

                if (errorMessage == null) {
                    backupFilePath = logPath + File.separator + "backup_instruction_" + date + ".sql";
                    errorMessage = performBackup.backupInstruction(conn, backupFilePath);
                }

                if (errorMessage == null) {
                    backupFilePath = logPath + File.separator + "backup_variable_" + date + ".sql";
                    errorMessage = performBackup.backupVariable(conn, backupFilePath);
                }

                if (errorMessage == null) {
                    backupFilePath = logPath + File.separator + "backup_reference_" + date + ".sql";
                    errorMessage = performBackup.backupReference(conn, backupFilePath);
                }

                if (errorMessage == null) {
                    backupFilePath = logPath + File.separator + "backup_component_block_" + date + ".sql";
                    errorMessage = performBackup.backupComponentBlock(conn, backupFilePath);
                }

                if (errorMessage == null) {
                    backupFilePath = logPath + File.separator + "backup_component_instruction_" + date + ".sql";
                    errorMessage = performBackup.backupComponentInstruction(conn, backupFilePath);
                }

                if (errorMessage == null) {
                    backupFilePath = logPath + File.separator + "backup_component_variable_" + date + ".sql";
                    errorMessage = performBackup.backupComponentVariable(conn, backupFilePath);
                }

                if (errorMessage == null) {
                    backupFilePath = logPath + File.separator + "backup_component_reference_" + date + ".sql";
                    errorMessage = performBackup.backupComponentReference(conn, backupFilePath);
                }

                if (errorMessage == null) {
                    showAlertTimer(
                            Alert.AlertType.INFORMATION,
                            "Backup DB Success!",
                            "Check the LOGS folder!",
                            "Database",
                            databaseChoiceBox.getValue(),
                            null,
                            null);

                } else {
                    String errorType = "Backup Database error";
                    String errorDetail = "Verify the backup script";

                    String detailedMessage = "Type: " + errorType + "\nDetail: " + errorDetail;

                    showAlertTimer(
                            Alert.AlertType.ERROR,
                            errorMessage.getErrorTitle(),
                            errorMessage.getErrorHeader(),
                            detailedMessage,
                            "Backup DB Scripts error",
                            databaseChoiceBox.getValue(),
                            null);
                }

            } catch (SQLException ex) {
                System.out.println(ex.getMessage());
            }
        }
    }

    private void runrRestoreScripts() {
        String dataBaseType = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);

        Label newInstruction =
                new Label("DB RESTORE\nDatabase Selected: \"" + dataBaseType + "\" \nLog Folder : \"v4.1f Beta Test\"");
        newInstruction.setStyle("-fx-font-size: 18px; -fx-text-fill: red;");

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, null, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText("Are you sure you want to EXECUTE RESTORE DB (\"" + dataBaseType + "\")?");
        alert.getDialogPane().setContent(newInstruction);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {

            try (Connection conn = performDataBase.getConnection()) {

                performBackup.initialize(conn);

                String logPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_LOG);
                String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"));

                String backupFilePath = logPath + File.separator + "backup_home_banking_" + date + ".sql";
                ErrorMessage errorMessage = performBackup.restoreHomeBanking(conn, backupFilePath);

                if (errorMessage == null) {
                    backupFilePath = logPath + File.separator + "backup_home_url_" + date + ".sql";
                    errorMessage = performBackup.restoreHomeUrl(conn, backupFilePath);
                }

                if (errorMessage == null) {
                    backupFilePath = logPath + File.separator + "backup_bot_job_" + date + ".sql";
                    errorMessage = performBackup.restoreBotJob(conn, backupFilePath);
                }
                if (errorMessage == null) {
                    backupFilePath = logPath + File.separator + "backup_block_" + date + ".sql";
                    errorMessage = performBackup.restoreBlock(conn, backupFilePath);
                }

                if (errorMessage == null) {
                    backupFilePath = logPath + File.separator + "backup_instruction_" + date + ".sql";
                    errorMessage = performBackup.restoreInstruction(conn, backupFilePath);
                }

                if (errorMessage == null) {
                    backupFilePath = logPath + File.separator + "backup_variable_" + date + ".sql";
                    errorMessage = performBackup.restoreVariable(conn, backupFilePath);
                }

                if (errorMessage == null) {
                    errorMessage = performBackup.restoreUpdateInstruction(conn);
                }

                if (errorMessage == null) {
                    backupFilePath = logPath + File.separator + "backup_reference_" + date + ".sql";
                    errorMessage = performBackup.restoreReference(conn, backupFilePath);
                }

                if (errorMessage == null) {
                    backupFilePath = logPath + File.separator + "backup_component_block_" + date + ".sql";
                    errorMessage = performBackup.restoreComponentBlock(conn, backupFilePath);
                }

                if (errorMessage == null) {
                    backupFilePath = logPath + File.separator + "backup_component_instruction_" + date + ".sql";
                    errorMessage = performBackup.restoreComponentInstruction(conn, backupFilePath);
                }

                if (errorMessage == null) {
                    backupFilePath = logPath + File.separator + "backup_component_variable_" + date + ".sql";
                    errorMessage = performBackup.restoreComponentVariable(conn, backupFilePath);
                }

                if (errorMessage == null) {
                    errorMessage = performBackup.restoreComponentUpdateInstruction(conn);
                }

                if (errorMessage == null) {
                    backupFilePath = logPath + File.separator + "backup_component_reference_" + date + ".sql";
                    errorMessage = performBackup.restoreComponentReference(conn, backupFilePath);
                }

                if (errorMessage == null) {
                    showAlertTimer(
                            Alert.AlertType.INFORMATION,
                            "Restore DB Success!",
                            "Check the LOGS folder!",
                            "Database",
                            databaseChoiceBox.getValue(),
                            null,
                            null);

                } else {

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
            } catch (SQLException ex) {
                System.out.println(ex.getMessage());
            }
        }
    }

    private void saveConfigurations() throws SQLException {
        boolean validfields = true;
        if (Strings.isNullOrEmpty(pathLicense.getText())) {
            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "License Path must be filed!", ButtonType.OK);
            validfields = false;
        }

        if (Strings.isNullOrEmpty(pathExcel.getText())) {
            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "Excel Path must be filed!", ButtonType.OK);
            validfields = false;
        }
        //        if (Strings.isNullOrEmpty(pathExport.getText())) {
        //            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "Export Path must be filed!",
        // ButtonType.OK);
        //            validfields = false;
        //        }
        //        if (Strings.isNullOrEmpty(fileExport.getText())) {
        //            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "File Name Export must be filed!",
        // ButtonType.OK);
        //            validfields = false;
        //        }

        if (Strings.isNullOrEmpty(pathLog.getText())) {
            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "Log Path must be filed!", ButtonType.OK);
            validfields = false;
        }

        if (Strings.isNullOrEmpty(dbUrl.getText())) {
            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "Java Path must be filed!", ButtonType.OK);
            validfields = false;
        }

        if (Strings.isNullOrEmpty(pathAccessDB.getText())) {
            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "Database Path must be filed!", ButtonType.OK);
            validfields = false;
        }
        if (Strings.isNullOrEmpty(pathReport.getText())) {
            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "Reports Path must be filed!", ButtonType.OK);
            validfields = false;
        }

        if (Strings.isNullOrEmpty(pathPriority.getText())) {
            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "Priority Path must be filed!", ButtonType.OK);
            validfields = false;
        }

        if (Strings.isNullOrEmpty(dbUser.getText())) {
            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "Database \"User\" must be filed!", ButtonType.OK);
            validfields = false;
        }

        if (Strings.isNullOrEmpty(dbPwd.getText())) {
            new ARAlertScene(
                    Alert.AlertType.ERROR, "Field Blank", "Database \"Password\" must be filed!", ButtonType.OK);
            validfields = false;
        }

        if (Strings.isNullOrEmpty(pathEngine.getText())) {
            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "AR Engine Path must be filed!", ButtonType.OK);
            validfields = false;
        }

        if (Strings.isNullOrEmpty(pathWebDriver.getText())) {
            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "Web Driver Path must be filed!", ButtonType.OK);
            validfields = false;
        }

        if (validfields) {

            arPropertyManager.setProperty(ARPropertyEnum.BROWSER.getValue(), browserChoiceBox.getValue());

            arPropertyManager.setProperty(
                    ARPropertyEnum.PATH_LICENSE.getValue(),
                    pathLicense.getText().trim());

            arPropertyManager.setProperty(
                    ARPropertyEnum.PATH_EXCEL.getValue(), pathExcel.getText().trim());

            arPropertyManager.setProperty(
                    ARPropertyEnum.PATH_LOG.getValue(), pathLog.getText().trim());
            arPropertyManager.setProperty(
                    ARPropertyEnum.PATH_PRIORITY.getValue(),
                    pathPriority.getText().trim());
            arPropertyManager.setProperty(
                    ARPropertyEnum.PATH_REPORT.getValue(), pathReport.getText().trim());
            arPropertyManager.setProperty(
                    ARPropertyEnum.PATH_ENGINE.getValue(), pathEngine.getText().trim());
            arPropertyManager.setProperty(
                    ARPropertyEnum.PATH_WEBDRIVER.getValue(),
                    pathWebDriver.getText().trim());

            try {
                performInitializer.testConnection(
                        databaseChoiceBox.getValue(),
                        pathAccessDB.getText().trim(),
                        dbUrl.getText(),
                        dbUser.getText().trim(),
                        dbPwd.getText().trim());
            } catch (Exception error) {
                ARLogger.getInstance(PerformDataBase.class).severe("testConnection Error: " + error.getMessage());
                performMessage.errorMessage(
                        "Database connection Failed",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>An error occurred during the Database connection.</span>",
                        "<span style='font-weight: bold;'>" + databaseChoiceBox.getValue() + "</span>.",
                        "<span style='color: #E65100; font-weight: bold;'>Please ensure the Database connections are correct.</span>",
                        "<span style='font-style: italic;'>Details: " + error.getMessage() + "</span>",
                        0);

                return;
            }

            if (arViewBotJobScene != null) {
                arViewBotJobScene.closeModal();
            }

            if (arScannedElementScene != null) {
                arScannedElementScene.closeModal();
            }

            if (arNewBotJobScene != null) {
                arNewBotJobScene.closeModal();
            }

            if (arNewCommandScene.getRowMoveDTO() != null) {
                arNewCommandScene.setRowMoveDTO(null);
                arNewCommandScene.closeModal();
            }

            if (arElementValueScene.getRowMoveDTO() != null) {
                arElementValueScene.setRowMoveDTO(null);
                arElementValueScene.closeModal();
            }

            arPropertyManager.setProperty(ARPropertyEnum.DATABASE_TYPE.getValue(), databaseChoiceBox.getValue());

            arPropertyManager.setProperty(
                    ARPropertyEnum.PATH_DB.getValue(), pathAccessDB.getText().trim());

            arPropertyManager.setProperty(ARPropertyEnum.DB_URL.getValue(), dbUrl.getText());

            arPropertyManager.setProperty(
                    ARPropertyEnum.DB_USER.getValue(), dbUser.getText().trim());

            arPropertyManager.setProperty(
                    ARPropertyEnum.DB_PWD.getValue(), dbPwd.getText().trim());

            performDataBase.changeDbConnection();

            //            performDataBase.dropPostGresSequences();
            //            performDataBase.exportHomeBanking();
            //            performDataBase.exportHomeUrl();
            //            performDataBase.exportBotJob();
            //            performDataBase.exportBlock();
            //            performDataBase.exportInstructions();
            //            performDataBase.exportVariables();
            //            performDataBase.exportReferences();

            homeBankingList.clear();
            homeBankingList.addAll(performDataBase.loadHomeBanking(null));
            homeBankingListView = new ListView<>(homeBankingList);

            arNewHomeBankingScene.initialize(homeBankingList);

            try {
                botJobList = FXCollections.observableArrayList(
                        performDataBase.loadAllBotJobs(performDataBase.getConnection()));
            } catch (Exception error) {
                throw error;
            }
            viewBotJobListView.setItems(botJobList);

            new ARAlertScene(
                    Alert.AlertType.INFORMATION,
                    "Configuration saved",
                    "The configuration has been saved and the data has been reloaded",
                    ButtonType.OK);
        }
    }

    private void deleteAllDB() {
        if (isEnabledLicence && !checkLicense()) {
            return;
        }

        String dataBaseType = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);

        Label newInstruction = new Label("DELETE ALL JOB DETAILS\nDatabase Selected: \"" + dataBaseType + "\"");
        newInstruction.setStyle("-fx-font-size: 18px; -fx-text-fill: red;");

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, null, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText("Are you sure you want to DELETE ALL JOB TABLES ROWS (\"" + dataBaseType + "\")?");
        alert.getDialogPane().setContent(newInstruction);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            if (performDataBase.deleteAllJobDetails(dataBaseType)) {
                // PerformDataBase..changeDbConnection();
                new ARAlertScene(
                        Alert.AlertType.INFORMATION,
                        "All Job Details has been deleted!",
                        "The All Instructions and Job Details has been deleted and the data has been reloaded",
                        ButtonType.OK);

            } else {
                // PerformDataBase..changeDbConnection();
                new ARAlertScene(
                        Alert.AlertType.ERROR,
                        "\"" + dataBaseType + "\" Problems\nNot possible  to delete All Job Details!",
                        "\"" + dataBaseType + "\" Problems!\n"
                                                        + "The Instructions and Job Details cannot be deleted and the data has been reloaded\n"
                                                        + dataBaseType
                                                != null
                                        && dataBaseType.equalsIgnoreCase("ACCESS")
                                ? dataBaseType + " database Recommendation:\nDelete interelly \"database.mdb\" file!"
                                : "",
                        ButtonType.OK);
            }
        }
    }

    private TextField createPathTextField(ARPropertyEnum property) {
        TextField textField = new TextField();
        textField.setText(arPropertyManager.getProperty(property));
        AnchorPane.setTopAnchor(textField, ARConstants.SPACE_ZERO);
        AnchorPane.setBottomAnchor(textField, ARConstants.SPACE_ZERO);
        AnchorPane.setRightAnchor(textField, ARConstants.SPACE_XL);
        AnchorPane.setLeftAnchor(textField, ARConstants.SPACE_ZERO);
        return textField;
    }

    private Button createPathButton() {
        Button button = builder.buildButton(
                "", ARConstants.SPACE_L, ARConstants.ICON_DIRECTORY, ARConstants.SPACE_M, new Insets(5D));
        button.setMaxWidth(ARConstants.SPACE_L);
        AnchorPane.setRightAnchor(button, 0D);
        return button;
    }

    private void openChooserFor(TextField field, Stage ownerStage, boolean isDirectory) {
        String folderBase = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
        if (Strings.isNullOrEmpty(folderBase)) {
            folderBase = System.getProperty("user.dir");
        }

        File startingPoint = new File(folderBase);
        String chosenPath =
                isDirectory ? openDirectoryChooserFor(startingPoint, ownerStage) : openFileChooserFor(startingPoint);
        if (!Strings.isNullOrEmpty(chosenPath)) {
            field.setText(chosenPath);
        }
    }

    private String openDirectoryChooserFor(File startingDirectory) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setInitialDirectory(startingDirectory);
        File chosenPath = chooser.showDialog(new Stage());
        return chosenPath.getAbsolutePath();
    }

    private String openFileChooserFor(File startingDirectory) {
        FileChooser chooser = new FileChooser();
        chooser.setInitialDirectory(startingDirectory);
        File chosenPath = chooser.showOpenDialog(new Stage());
        return chosenPath.getAbsolutePath();
    }

    private String openDirectoryChooserFor(File startingDirectory, Stage ownerStage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setInitialDirectory(startingDirectory);

        // Make sure the dialog is shown in front of the provided stage
        File chosenPath = chooser.showDialog(ownerStage);
        return chosenPath != null ? chosenPath.getAbsolutePath() : null;
    }

    private void showAlertTimer(
            Alert.AlertType alertType,
            String title,
            String header,
            String msg1,
            String msg2,
            String msg3,
            String msg4) {
        executorService = Executors.newSingleThreadExecutor();
        alertToShow.setAlertType(alertType);
        alertToShow.setTitle(title);
        alertToShow.setHeaderText(header);
        //        alertToShow.setContentText(content);

        // Remove the border of the DialogPane
        alertToShow.getDialogPane().setStyle("-fx-border-color: transparent; -fx-border-width: 0;");

        // Create VBox to hold multiple Text elements
        VBox allMsgVer = new VBox();
        allMsgVer.setSpacing(10); // Add some spacing between texts
        allMsgVer.setPadding(new Insets(20));

        Text variableText1Styled = new Text(msg1);
        Text variableText2Styled = new Text(msg2);
        Text variableText3Styled = new Text(msg3);
        Text variableText4Styled = new Text(msg4);

        // Set styles based on alert type
        if (alertType.equals(Alert.AlertType.ERROR)) {
            // Change the font color of the title to red
            variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");
            variableText2Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");
            variableText3Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");
            variableText4Styled.setStyle("-fx-font-size: 18px; -fx-fill: red;");
        } else {
            // Change the font color of the title to red
            variableText1Styled.setStyle("-fx-font-size: 18px; -fx-fill: blue;");
            variableText2Styled.setStyle("-fx-font-size: 18px; -fx-fill: green;");
            variableText3Styled.setStyle("-fx-font-size: 18px; -fx-fill: green;");
            variableText4Styled.setStyle("-fx-font-size: 18px; -fx-fill: green;");
        }

        // Add Text elements to VBox
        if (msg1 != null && msg2 == null && msg3 == null && msg4 == null) {
            allMsgVer.getChildren().addAll(variableText1Styled);
        } else if (msg1 != null && msg2 != null && msg3 == null && msg4 == null) {
            allMsgVer.getChildren().addAll(variableText1Styled, variableText2Styled);
        } else if (msg1 != null && msg2 != null && msg3 != null && msg4 == null) {
            allMsgVer.getChildren().addAll(variableText1Styled, variableText2Styled, variableText3Styled);
        } else {
            allMsgVer.getChildren().addAll(variableText1Styled, variableText2Styled, variableText3Styled);
        }

        // Create a StackPane to hold the VBox
        StackPane stackPane = new StackPane(allMsgVer);
        stackPane.setPadding(new Insets(20));

        // Set StackPane content to alert dialog pane
        alertToShow.getDialogPane().setContent(stackPane);

        executorService.execute(() -> {
            timeline.setCycleCount(SECONDS); // Run for seconds
            timeline.play(); // Start the timeline

            // Show the alert on the JavaFX Application Thread
            javafx.application.Platform.runLater(() -> alertToShow.showAndWait());
        });

        if (executorService != null) {
            remainingSeconds = SECONDS;
            executorService.shutdown();
        }
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
                msgColor = "#C62828"; // Soft, elegant red tone

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
            ARLogger.getInstance(ARConfigurationPane.class)
                    .severe("Cannot read/validate the License path/file. Error: " + error.getMessage());
            return false;
        }
    }
}
