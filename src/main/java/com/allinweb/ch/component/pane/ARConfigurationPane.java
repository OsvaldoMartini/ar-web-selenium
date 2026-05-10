package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.listCell.ARCellFactory;
import com.allinweb.ch.component.listCell.HomeBankingListCell;
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
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ARConfigurationPane extends ARPane {

    private static final ARComponentBuilder builder = ARComponentBuilder.getInstance();
    private static final int SECONDS = 3; // Total seconds for the countdown
    private static final ARPropertyManager arPropertyManager;
    private static final ARNewHomeBankingScene arNewHomeBankingScene;
    private static final PerformMessage performMessage;
    private static final PerformLists performLists;
    private static final PerformDBEngine performDBEngine;
    private static final PerformDataBase performDataBase;
    private static final PerformBackup performBackup;
    private static final PerformInitializer performInitializer;
    private static final ARWebDriver arWebDriver = ARWebDriver.getInstance();
    private static final ARScannedElementScene arScannedElementScene;
    private static final ARViewBotJobScene arViewBotJobScene;
    private static final ARNewCommandScene arNewCommandScene;
    private static final ARElementValueScene arElementValueScene;
    private static final ARNewBotJobScene arNewBotJobScene;
    protected static volatile ARConfigurationPane instance;
    protected static volatile ARMainPane arMainPane = ARMainPane.getInstance();

    // Static block to initialize
    static {
        arScannedElementScene = ARScannedElementScene.getInstance();
        arNewCommandScene = ARNewCommandScene.getInstance();
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
    Label restoreDateLabel;
    Label deleteAllDBLabel;
    Label insertSitesLabel;
    Label pathWebDriverLabel;
    Label pathAppiumLabel;
    Label pathPluginsLabel;
    Label urlPluginsLabel;
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
    TextField pathAppium;
    TextField pathPlugins;
    TextField urlPlugins;
    ChoiceBox<String> browserChoiceBox = new ChoiceBox<>();
    ChoiceBox<String> databaseChoiceBox = new ChoiceBox<>();
    ObservableList<String> browserList =
            FXCollections.observableArrayList(ARConstants.CHROME, ARConstants.EDGE, ARConstants.FIREFOX);
    ObservableList<String> databaseList =
            FXCollections.observableArrayList(ARConstants.ACCESS, ARConstants.POSTGRES, ARConstants.SQLITE);
    Button pathExcelButton;
    Button pathLicenseButton;
    //    Button pathExportButton;
    Button pathLogButton;
    Button pathAccessDBButton;
    Button pathReportButton;
    Button pathPriorityButton;
    Button pathEngineButton;
    Button pathWebDriverButton;
    Button pathAppiumButton;
    Button pathPluginsButton;
    Button reloadDBButton;
    Button backupDBButton;
    Button restoreDBButton;
    Button deleteAllDBButton;
    Button insertSitesdButton;
    HBox backupRestoreGroup;
    DatePicker restoreDatePicker;
    VBox pathGroup;
    AnchorPane mainPane;
    private ListView<BotJobLoadDTO> viewBotJobListView;
    private Stage modalStage;
    private boolean isEnabledLicence;
    private String previousDB;
    private String previousDBUrl;
    private int remainingSeconds = SECONDS;
    private Timeline timeline;
    private ExecutorService executorService;
    private Alert alertToShow;
    private ListView<HomeBankingLoadDTO> homeBankingListView;

    // Private constructor to prevent instantiation
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

    public void initialize(Stage modalStage, ListView<BotJobLoadDTO> viewBotJobListView, boolean isEnabledLicence) {
        this.isEnabledLicence = isEnabledLicence;
        this.modalStage = modalStage;
        this.viewBotJobListView = viewBotJobListView;
    }

    @Override
    public Pane getPaneReference() {
        return mainPane;
    }

    @Override
    public void initUIComponents() {

        this.previousDB = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);
        this.previousDBUrl = arPropertyManager.getProperty(ARPropertyEnum.DB_URL);

        startAlertShowTimer();

        title = new Label("Configuration");
        title.setMaxHeight(ARConstants.SPACE_L);
        title.setBackground(new Background(
                new BackgroundFill(Color.ROYALBLUE, new CornerRadii(ARConstants.SPACE_XS), Insets.EMPTY)));
        title.setTextFill(Color.WHITE);
        AnchorPane.setTopAnchor(title, ARConstants.SPACE_M);
        AnchorPane.setLeftAnchor(title, ARConstants.SPACE_M);
        AnchorPane.setRightAnchor(title, ARConstants.SPACE_M);

        //        ButtonBar homeBankingActionGroup = new ButtonBar();
        insertSitesdButton = builder.buildButton("Insert Organizations");
        insertSitesdButton.setMinWidth(Region.USE_PREF_SIZE);

        //        homeBankingActionGroup.getButtons().addAll(addHomeBankingButton);

        //        ObservableList<HomeBankingDTO> homeBankingList =
        //                PerformDataBase..getEntityList(HomeBankingDTO.class);

        if (performDataBase.isConnDBWorks()) {
            ErrorMessage errorMessage = performDBEngine.loadHomeBanking(null);
            if (errorMessage != null) {
                performMessage.errorMessageOperationFailed(errorMessage);
            }
        }

        homeBankingListView = new ListView<>(FXCollections.observableArrayList(performLists.getListHomeBanking()));
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
        pathAccessDBLabel = new Label("Database Path:");
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

        browserLabel = new Label("Browser");
        databaseLabel = new Label("DB Type");
        backupDBLabel = new Label("Backup DB  Restore DB");
        restoreDateLabel = new Label("Date Restore");
        reloadDBLabel = new Label("Reload DB");
        deleteAllDBLabel = new Label("Delete ALL DB");
        insertSitesLabel = new Label("Insert Sites");

        backupDBButton = builder.buildButton("Backup DB");
        backupDBButton.setMaxHeight(ARConstants.SPACE_XXS);
        backupDBButton.setMinWidth(Region.USE_PREF_SIZE);
        backupDBButton.setStyle("-fx-font-size: 12px;");

        restoreDBButton = builder.buildButton("Restore DB");
        restoreDBButton.setMaxHeight(ARConstants.SPACE_XXS);
        restoreDBButton.setMinWidth(Region.USE_PREF_SIZE);
        restoreDBButton.setStyle("-fx-font-size: 12px;");

        backupDBButton.setDisable(false);

        restoreDatePicker = new DatePicker(LocalDate.now());
        restoreDatePicker.setPrefWidth(140);
        restoreDatePicker.setStyle("-fx-font-size: 12px;");
        restoreDatePicker.setMaxHeight(28); // match button height

        backupRestoreGroup = new HBox(10); // More spacing for clarity
        backupRestoreGroup.setAlignment(Pos.CENTER);
        //        backupRestoreGroup.setPadding(new Insets(2, 0, 2, 0)); // Add light vertical padding

        backupRestoreGroup.getChildren().addAll(backupDBButton, restoreDBButton, restoreDatePicker);

        reloadDBButton = builder.buildButton("Reload Configs");
        reloadDBButton.setMaxHeight(ARConstants.SPACE_L);
        reloadDBButton.setMinWidth(Region.USE_PREF_SIZE);

        deleteAllDBButton = builder.buildButton("Delete DB");
        deleteAllDBButton.setMaxHeight(ARConstants.SPACE_L);
        deleteAllDBButton.setMinWidth(Region.USE_PREF_SIZE);
        deleteAllDBButton.setStyle("-fx-background-color: lightcoral; -fx-text-fill: blue;");

        browserChoiceBox.setItems(browserList);
        browserChoiceBox.setMinWidth(Region.USE_PREF_SIZE);

        databaseChoiceBox.setItems(databaseList);
        databaseChoiceBox.setMinWidth(Region.USE_PREF_SIZE);
        databaseChoiceBox.setDisable(false);

        HBox buttonRow = new HBox(10); // spacing between columns
        buttonRow.setAlignment(Pos.CENTER);
        buttonRow.setPadding(new Insets(5, 0, 5, 0)); // optional

        // Column 1: Browser
        VBox browserColumn = new VBox(2);
        browserColumn.getChildren().addAll(browserLabel, browserChoiceBox);

        // Column 2: DB Type
        VBox databaseColumn = new VBox(2);
        databaseColumn.getChildren().addAll(databaseLabel, databaseChoiceBox);
        databaseColumn.setDisable(false);

        // Column 3: Reload DB
        VBox reloadColumn = new VBox(2);
        reloadColumn.getChildren().addAll(reloadDBLabel, reloadDBButton);

        // Column 4: Backup & Restore Group (keep as-is)
        VBox backupColumn = new VBox(2);
        backupColumn.getChildren().addAll(backupDBLabel, backupRestoreGroup);

        // Column 5: Restore Date Picker
        VBox dateColumn = new VBox(2);
        dateColumn.getChildren().addAll(restoreDateLabel, restoreDatePicker);

        // Column 6: Delete DB
        VBox deleteColumn = new VBox(2);
        deleteColumn.getChildren().addAll(deleteAllDBLabel, deleteAllDBButton);

        // Column 7: Insert Sites
        VBox insertSitesColumn = new VBox(2);
        insertSitesColumn.getChildren().addAll(insertSitesLabel, insertSitesdButton);

        // Add all columns to the row
        buttonRow
                .getChildren()
                .addAll(
                        browserColumn,
                        databaseColumn,
                        reloadColumn,
                        backupColumn,
                        dateColumn,
                        deleteColumn,
                        insertSitesColumn);

        //        AnchorPane logGroup = new AnchorPane(pathLog, sizeLog, pathLogButton);
        dbUrlLabel = new Label("Database URL:");
        dbUrlLabel.setVisible(false);
        dbUrlLabel.setManaged(false);
        dbUrl = createPathTextField(ARPropertyEnum.DB_URL);
        dbUrl.setVisible(false);
        dbUrl.setManaged(false);

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
        dbUserPwdGroup.setVisible(false);
        dbUserPwdGroup.setManaged(false);
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

        pathAppiumLabel = new Label("Appium Path:");
        pathAppiumLabel.setVisible(false);
        pathAppiumLabel.setManaged(false);
        pathAppium = createPathTextField(ARPropertyEnum.PATH_APPIUM);
        pathAppiumButton = createPathButton();
        AnchorPane appiumGroup = new AnchorPane(pathAppium, pathAppiumButton);
        appiumGroup.setVisible(false);
        appiumGroup.setManaged(false);

        pathPluginsLabel = new Label("Plugins Path:");
        pathPlugins = createPathTextField(ARPropertyEnum.PATH_PLUGINS);
        pathPluginsButton = createPathButton();
        AnchorPane pluginsGroup = new AnchorPane(pathPlugins, pathPluginsButton);

        urlPluginsLabel = new Label("URL Plugins:");
        urlPluginsLabel.setVisible(false);
        urlPluginsLabel.setManaged(false);
        urlPlugins = createPathTextField(ARPropertyEnum.URL_PLUGINS);
        urlPlugins.setPromptText("https://yourserver.com/plugins/latest.zip");
        urlPlugins.setVisible(false);
        urlPlugins.setManaged(false);

        Label organizationsLabel = new Label("Organizations");
        organizationsLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1565C0;");
        organizationsLabel.setAlignment(Pos.CENTER);
        organizationsLabel.setMaxWidth(Double.MAX_VALUE);

        // ── Collapsible Section Headers ─────────────────────────────────
        // Plain Label + VBox + click-to-toggle. No Accordion/TitledPane.
        // Maps directly to Swing: JPanel + setVisible() + MouseListener.

        // Operational section content
        VBox operationalContent = new VBox(
                pathLicenseLabel,
                licenseGroup,
                pathExcelLabel,
                excelGroup,
                gridPaneLog,
                gridPaneDB,
                pathReportLabel,
                reportGroup,
                pathPriorityLabel,
                priorityGroup,
                pathEngineLabel,
                engineGroup,
                pathWebDriverLabel,
                driverGroup);
        operationalContent.setSpacing(2);
        operationalContent.setPadding(new Insets(4, 0, 4, 0));

        // Advanced section content
        VBox advancedContent = new VBox(
                pathAppiumLabel,
                appiumGroup,
                pathPluginsLabel,
                pluginsGroup,
                urlPluginsLabel,
                urlPlugins,
                dbUrlLabel,
                dbUrl,
                dbUserPwdGroup);
        advancedContent.setSpacing(2);
        advancedContent.setPadding(new Insets(4, 0, 4, 0));
        advancedContent.setVisible(false);
        advancedContent.setManaged(false); // collapsed by default

        // Section header style
        String headerStyle = "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1565C0;"
                + "-fx-padding: 6 10 6 10; -fx-background-color: #E3F2FD;"
                + "-fx-background-radius: 4; -fx-cursor: hand;";

        Label operationalHeader = new Label("▼  Operational Configuration");
        operationalHeader.setMaxWidth(Double.MAX_VALUE);
        operationalHeader.setStyle(headerStyle);

        Label advancedHeader = new Label("▶  Advanced Configuration");
        advancedHeader.setMaxWidth(Double.MAX_VALUE);
        advancedHeader.setStyle(headerStyle);

        // Click toggles: opening one closes the other (accordion behavior)
        operationalHeader.setOnMouseClicked(e -> {
            boolean willOpen = !operationalContent.isVisible();
            // Close the other section
            advancedContent.setVisible(false);
            advancedContent.setManaged(false);
            advancedHeader.setText("▶  Advanced Configuration");
            // Toggle this section
            operationalContent.setVisible(willOpen);
            operationalContent.setManaged(willOpen);
            operationalHeader.setText(willOpen ? "▼  Operational Configuration" : "▶  Operational Configuration");
        });

        advancedHeader.setOnMouseClicked(e -> {
            boolean willOpen = !advancedContent.isVisible();
            // Close the other section
            operationalContent.setVisible(false);
            operationalContent.setManaged(false);
            operationalHeader.setText("▶  Operational Configuration");
            // Toggle this section
            advancedContent.setVisible(willOpen);
            advancedContent.setManaged(willOpen);
            advancedHeader.setText(willOpen ? "▼  Advanced Configuration" : "▶  Advanced Configuration");
        });

        // ── Main layout ──────────────────────────────────────────────────
        pathGroup = new VBox(
                operationalHeader,
                operationalContent,
                advancedHeader,
                advancedContent,
                buttonRow,
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

        Platform.runLater(() -> {
            double choiceBoxHeight = databaseChoiceBox.getHeight();

            if (choiceBoxHeight > 0) {
                reloadDBButton.setMinHeight(choiceBoxHeight);
                reloadDBButton.setPrefHeight(choiceBoxHeight);
                reloadDBButton.setMaxHeight(choiceBoxHeight);

                backupDBButton.setMinHeight(choiceBoxHeight);
                backupDBButton.setPrefHeight(choiceBoxHeight);
                backupDBButton.setMaxHeight(choiceBoxHeight);

                restoreDBButton.setMinHeight(choiceBoxHeight);
                restoreDBButton.setPrefHeight(choiceBoxHeight);
                restoreDBButton.setMaxHeight(choiceBoxHeight);

                restoreDatePicker.setMinHeight(choiceBoxHeight);
                restoreDatePicker.setPrefHeight(choiceBoxHeight);
                restoreDatePicker.setMaxHeight(choiceBoxHeight);

                deleteAllDBButton.setMinHeight(choiceBoxHeight);
                deleteAllDBButton.setPrefHeight(choiceBoxHeight);
                deleteAllDBButton.setMaxHeight(choiceBoxHeight);

                insertSitesdButton.setMinHeight(choiceBoxHeight);
                insertSitesdButton.setPrefHeight(choiceBoxHeight);
                insertSitesdButton.setMaxHeight(choiceBoxHeight);
            }
        });
    }

    @Override
    public void initUIBehaviour() {

        // Add listener to databaseChoiceBox
        databaseChoiceBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (ARConstants.ACCESS.equals(newVal) || ARConstants.SQLITE.equals(newVal)) {
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

        if (performDataBase.isConnDBWorks()) {
            try (Connection conn = performDataBase.getConnection()) {
                if (conn != null) {
                    if (performLists.getListHomeBanking().isEmpty()) {
                        backupDBButton.setDisable(true);
                    }
                }
            } catch (SQLException ignore) {
                log.info("Check if It Was Migrated! - Not Migrate Columns found!");
            }
        }

        insertSitesdButton.setOnMouseClicked(e -> {
            if (isEnabledLicence && !checkLicense()) {
                return;
            }
            HomeBankingLoadDTO homeBank = performLists.getFirstHomeBanking();
            arNewHomeBankingScene.initialize(homeBank);
            Stage currentStage = (Stage) insertSitesdButton.getScene().getWindow();
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
        pathAppiumButton.setOnMouseClicked(e -> openChooserFor(pathAppium, modalStage, true));
        pathPluginsButton.setOnMouseClicked(e -> openChooserFor(pathPlugins, modalStage, true));

        browserChoiceBox.setValue(arPropertyManager.getProperty(ARPropertyEnum.BROWSER));

        if (arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE) != null) {
            databaseChoiceBox.setValue(arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE));
        } else {
            databaseChoiceBox.setValue("Access");
            //            databaseChoiceBox.setValue(arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE));
        }

        reloadDBButton.setOnMouseClicked(e -> {
            try {
                saveConfigurations();
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        });
        backupDBButton.setOnMouseClicked(e -> runBackupScripts());
        restoreDBButton.setOnMouseClicked(e -> runRestoreScripts());
        deleteAllDBButton.setOnMouseClicked(e -> deleteAllDB());
    }

    private void runBackupScripts() {
        String dataBaseType = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"));

        if (dataBaseType.equalsIgnoreCase(databaseChoiceBox.getValue().trim())) {
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
                            + databaseChoiceBox.getValue().trim(),
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
                        log.info("Backup created: " + backupFile.getAbsolutePath());
                    } catch (java.io.IOException e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception ex) {
                log.info(ex.getMessage());
            }
        }

        // Let the user choose the destination folder for the backup file.
        // Default to PATH_DB so repeated backups land in the same place unless
        // the user says otherwise.
        String defaultDbPath = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
        File defaultDir = (defaultDbPath == null || defaultDbPath.isBlank()) ? null : new File(defaultDbPath);
        Stage ownerStageBackup = (Stage) backupDBButton.getScene().getWindow();
        String chosenBackupFolder = openDirectoryChooserFor(defaultDir, ownerStageBackup);
        if (chosenBackupFolder == null || chosenBackupFolder.isBlank()) {
            // User cancelled the folder picker — abort silently.
            return;
        }

        // File name carries the dialect used to produce it, so a folder can hold
        // snapshots from each engine side-by-side without colliding:
        //   backup_sqlite_all_YYYY_MM_DD.sql   (DATABASE_TYPE = TEXT)
        //   backup_access_all_YYYY_MM_DD.sql   (DATABASE_TYPE = Access)
        //   backup_postgres_all_YYYY_MM_DD.sql (DATABASE_TYPE = Postgres / PostGres)
        String backupFileName = "backup_" + dbDialectSlug(dataBaseType) + "_all_" + date + ".sql";

        ARExecution.DialogModal respModal = performMessage.showCustomModalDialogDragWin11(
                "Backup Database Confirmation",
                "<span style='font-weight: bold; color: #D32F2F;'>Are you sure you want to execute a database backup?</span>",
                "Target database: <b>" + dataBaseType + "</b>",
                "<span style='color: #6A1B9A;'>Destination folder:</span> " + chosenBackupFolder,
                "<span style='font-style: italic;'>Output file: <b>" + backupFileName
                        + "</b>. For Access / SQLite a timestamped binary copy of the database file is dropped in the"
                        + " same folder for emergency recovery.</span>",
                false,
                "Execute Backup",
                "Cancel",
                0);

        if (!respModal.equals(ARExecution.DialogModal.STOP)) {
            try (Connection conn = performDataBase.getConnection()) {

                performBackup.initialize(conn);

                // Single-file backup: one .sql containing every table and every row in
                // FK-safe order, byte-compatible with the legacy per-table format.
                String backupFilePath = chosenBackupFolder + File.separator + backupFileName;
                ErrorMessage errorMessage = performBackup.dumpAllToSingleFile(conn, backupFilePath);

                // For Access / SQLite also drop a timestamped binary copy of the
                // DB file into the backup folder alongside the SQL dump, so the
                // folder is self-contained for emergency recovery. No-op for Postgres.
                if (errorMessage == null) {
                    String defaultDbFolder = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);
                    errorMessage = performBackup.copyDbFileTo(dataBaseType, defaultDbFolder, chosenBackupFolder);
                }

                if (errorMessage == null) {
                    performMessage.showCustomModalDialogDragWin11(
                            "Backup DB Success! ✅",
                            "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>Database backup completed successfully!</span>",
                            "<span style='color: #1565C0; font-weight: bold;'>All tables were dumped in FK-safe order.</span>",
                            "<span style='color: #6A1B9A; font-weight: bold;'>Database:</span> "
                                    + databaseChoiceBox.getValue()
                                    + "<br/><span style='color: #6A1B9A; font-weight: bold;'>Folder:</span> "
                                    + chosenBackupFolder
                                    + "<br/><span style='color: #6A1B9A; font-weight: bold;'>File:</span> "
                                    + backupFileName,
                            "<span style='color: #E65100; font-weight: bold;'>💡 Tip:</span> Keep this folder safe —"
                                    + " you can use it with <span style='text-decoration: underline;'>Restore DB</span> to recover this snapshot.",
                            false,
                            "OK",
                            null,
                            0);
                } else {
                    log.error("Backup Database error: " + errorMessage.getErrorMessage());
                    performMessage.errorMessage(
                            "Backup Database error",
                            "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>"
                                    + errorMessage.getErrorTitle() + "</span> ❌",
                            "<span style='color: #E65100; font-weight: bold;'>Error Type:</span> "
                                    + errorMessage.getErrorHeader(),
                            "<span style='font-style: italic;'>Detail:</span> " + errorMessage.getErrorMessage(),
                            null,
                            0);
                }

            } catch (SQLException ex) {
                log.info(ex.getMessage());
            }
        }
    }

    private void runRestoreScripts() {

        LocalDate selectedDate = restoreDatePicker.getValue();

        String formattedDate;
        if (selectedDate != null) {
            formattedDate = selectedDate.format(DateTimeFormatter.ofPattern("yyyy_MM_dd"));
            log.info("Selected backup date: " + formattedDate);
        } else {
            Label dateSelection = new Label(
                    "Please select a date to restore from.\n" + "Check the database directory for available backups.");
            dateSelection.setWrapText(true);

            Alert alert = new Alert(Alert.AlertType.WARNING, null, ButtonType.OK);
            alert.setTitle("Restore Warning");
            alert.setHeaderText("No Date Selected");
            alert.getDialogPane().setContent(dateSelection);
            alert.showAndWait();
            return;
        }

        String dataBaseType = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);
        String dataBaseFolder = arPropertyManager.getProperty(ARPropertyEnum.PATH_DB);

        if (dataBaseType.equalsIgnoreCase(databaseChoiceBox.getValue().trim())) {

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
                            + databaseChoiceBox.getValue().trim(),
                    "<span style='color: #6A1B9A; font-weight: bold;'>Saved Database:</span> "
                            + dataBaseType + "<br/>"
                            + "<span style='color: #E65100; font-weight: bold;'>💡 Reminder:</span> Press the <span style='text-decoration: underline;'>Reload Configs</span> button to save and apply your database choice before continuing.",
                    false,
                    "OK",
                    null,
                    0);
            return;
        }

        // Let the user choose the source folder to restore from. Default to PATH_DB.
        File defaultRestoreDir = (dataBaseFolder == null || dataBaseFolder.isBlank()) ? null : new File(dataBaseFolder);
        Stage ownerStageRestore = (Stage) restoreDBButton.getScene().getWindow();
        String chosenRestoreFolder = openDirectoryChooserFor(defaultRestoreDir, ownerStageRestore);
        if (chosenRestoreFolder == null || chosenRestoreFolder.isBlank()) {
            // User cancelled the folder picker — abort silently.
            return;
        }

        // Detect which backup format is present in the chosen folder for the given date.
        // Probed in order of preference:
        //   1. Dialect-prefixed single file: <folder>/backup_<dialect>_all_<date>.sql
        //                                    (written by the current backup code)
        //   2. Dialect-less single file:     <folder>/backup_all_<date>.sql
        //                                    (previous single-file naming, kept for compat)
        //   3. Legacy per-table backups:     <folder>/backup_home_banking_<date>.sql (marker)
        File dialectSingleFile = new File(
                chosenRestoreFolder, "backup_" + dbDialectSlug(dataBaseType) + "_all_" + formattedDate + ".sql");
        File plainSingleFile = new File(chosenRestoreFolder, "backup_all_" + formattedDate + ".sql");
        File legacyMarker = new File(chosenRestoreFolder, "backup_home_banking_" + formattedDate + ".sql");

        File singleFile =
                dialectSingleFile.exists() ? dialectSingleFile : (plainSingleFile.exists() ? plainSingleFile : null);
        boolean useSingleFile = singleFile != null;
        boolean useLegacyFiles = !useSingleFile && legacyMarker.exists();

        if (!useSingleFile && !useLegacyFiles) {
            performMessage.showCustomModalDialogDragWin11(
                    "No Backup Found ⚠️",
                    "<span style='color: #D32F2F; font-weight: bold;'>No backup for that date was found in the chosen folder.</span>",
                    "<span style='color: #1565C0;'>Looked for</span> <b>" + dialectSingleFile.getName() + "</b>, <b>"
                            + plainSingleFile.getName()
                            + "</b>, <span style='color: #1565C0;'>and the legacy</span> <b>"
                            + legacyMarker.getName() + "</b>.",
                    "<span style='color: #6A1B9A;'>Folder:</span> " + chosenRestoreFolder,
                    "<span style='font-style: italic;'>Pick another folder or date and try again.</span>",
                    false,
                    "OK",
                    null,
                    0);
            return;
        }

        String formatLabel = useSingleFile
                ? "Single-file backup: <b>" + singleFile.getName() + "</b>"
                : "Legacy per-table backups (11 files for <b>" + formattedDate + "</b>)";
        String formatNote = useSingleFile
                ? "The database will be fully restored from one .sql file (FK-safe order, row ids restart at 1 and all FKs re-keyed)."
                : "No <b>" + dialectSingleFile.getName() + "</b> or <b>" + plainSingleFile.getName()
                        + "</b> found — falling back to the legacy 11-file restore.";

        ARExecution.DialogModal respModal = performMessage.showCustomModalDialogDragWin11(
                "Restore Database Confirmation",
                "<span style='font-weight: bold; color: #D32F2F;'>Are you sure you want to execute a database restore?</span>",
                "Detected format: " + formatLabel,
                "<span style='color: #6A1B9A;'>Source folder:</span> " + chosenRestoreFolder,
                "<span style='font-style: italic;'>" + formatNote + " Target database: <b>" + dataBaseType
                        + "</b> at <b>" + dataBaseFolder + "</b>.</span>",
                false,
                "Execute Restore",
                "Cancel",
                0);

        if (!respModal.equals(ARExecution.DialogModal.STOP)) {
            // One restore path for every dialect: restoreWithRemap wipes the
            // backed-up tables, resets the identity counter to 1 (SQLite via
            // sqlite_sequence, Postgres via setval, Access via COUNTER's
            // post-DELETE behaviour) and replays the dump through the legacy
            // per-table methods that re-key FKs using old→new id maps.
            // Fresh ids start at 1 on every table. The binary safety copy
            // taken at BACKUP time lives in the chosen backup folder and is
            // enough for emergency recovery.
            try (Connection conn = performDataBase.getConnection()) {

                performBackup.initialize(conn);

                ErrorMessage errorMessage = null;

                if (useSingleFile) {
                    errorMessage = performBackup.restoreWithRemap(conn, singleFile.getAbsolutePath());
                } else if (!useSingleFile) {
                    // Legacy 11-file path — preserved for restoring backups taken before
                    // backup_all_<date>.sql existed. Delegates to the old per-table
                    // methods in the same order they were called historically.
                    errorMessage = runLegacyPerTableRestore(conn, chosenRestoreFolder, formattedDate);
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
                    viewBotJobListView.setItems(FXCollections.observableArrayList(performLists.getQuickBotJobs()));

                    backupDBButton.setDisable(performLists.getListHomeBanking().isEmpty());
                    homeBankingListView.setItems(FXCollections.observableArrayList(performLists.getListHomeBanking()));

                    HomeBankingLoadDTO homeBank = performLists.getFirstHomeBanking();
                    arNewHomeBankingScene.initialize(homeBank);

                    performMessage.showCustomModalDialogDragWin11(
                            "Restore DB Success! ✅",
                            "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>Database restored successfully!</span>",
                            "<span style='color: #1565C0; font-weight: bold;'>Now you can start to use your database!</span>",
                            "<span style='color: #6A1B9A; font-weight: bold;'>Database:</span> "
                                    + databaseChoiceBox.getValue(),
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
    }

    /**
     * Legacy 11-file restore chain — kept so the UI can fall back to restoring
     * backups taken before {@code backup_all_<date>.sql} existed. This is the
     * exact sequence {@link #runRestoreScripts()} used to call inline before the
     * single-file path was introduced.
     */
    private ErrorMessage runLegacyPerTableRestore(Connection conn, String folder, String formattedDate) {
        String backupFilePath = folder + File.separator + "backup_home_banking_" + formattedDate + ".sql";
        ErrorMessage errorMessage = performBackup.restoreHomeBanking(conn, backupFilePath);

        if (errorMessage == null) {
            backupFilePath = folder + File.separator + "backup_home_url_" + formattedDate + ".sql";
            errorMessage = performBackup.restoreHomeUrl(conn, backupFilePath);
        }
        if (errorMessage == null) {
            backupFilePath = folder + File.separator + "backup_bot_job_" + formattedDate + ".sql";
            errorMessage = performBackup.restoreBotJob(conn, backupFilePath, null, null, null);
        }
        if (errorMessage == null) {
            backupFilePath = folder + File.separator + "backup_block_" + formattedDate + ".sql";
            errorMessage = performBackup.restoreBlock(conn, backupFilePath, null);
        }
        if (errorMessage == null) {
            backupFilePath = folder + File.separator + "backup_instruction_" + formattedDate + ".sql";
            errorMessage = performBackup.restoreInstruction(conn, backupFilePath, null);
        }
        if (errorMessage == null) {
            backupFilePath = folder + File.separator + "backup_variable_" + formattedDate + ".sql";
            errorMessage = performBackup.restoreVariable(conn, backupFilePath, null);
        }
        if (errorMessage == null) {
            errorMessage = performBackup.restoreUpdateInstruction(conn, null);
        }
        if (errorMessage == null) {
            backupFilePath = folder + File.separator + "backup_reference_" + formattedDate + ".sql";
            errorMessage = performBackup.restoreReference(conn, backupFilePath, null);
        }
        if (errorMessage == null) {
            backupFilePath = folder + File.separator + "backup_component_block_" + formattedDate + ".sql";
            errorMessage = performBackup.restoreComponentBlock(conn, backupFilePath);
        }
        if (errorMessage == null) {
            backupFilePath = folder + File.separator + "backup_component_instruction_" + formattedDate + ".sql";
            errorMessage = performBackup.restoreComponentInstruction(conn, backupFilePath);
        }
        if (errorMessage == null) {
            backupFilePath = folder + File.separator + "backup_component_variable_" + formattedDate + ".sql";
            errorMessage = performBackup.restoreComponentVariable(conn, backupFilePath);
        }
        if (errorMessage == null) {
            errorMessage = performBackup.restoreComponentUpdateInstruction(conn);
        }
        if (errorMessage == null) {
            backupFilePath = folder + File.separator + "backup_component_reference_" + formattedDate + ".sql";
            errorMessage = performBackup.restoreComponentReference(conn, backupFilePath);
        }
        return errorMessage;
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

        //        if (Strings.isNullOrEmpty(pathAppium.getText())) {
        //            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "Appium Path must be filed!",
        // ButtonType.OK);
        //            validfields = false;
        //        }

        if (Strings.isNullOrEmpty(pathPlugins.getText())) {
            new ARAlertScene(Alert.AlertType.ERROR, "Field Blank", "Plugins Path must be filed!", ButtonType.OK);
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
            //            arPropertyManager.setProperty(

            //                    ARPropertyEnum.PATH_APPIUM.getValue(), pathAppium.getText().trim());
            arPropertyManager.setProperty(
                    ARPropertyEnum.PATH_PLUGINS.getValue(),
                    pathPlugins.getText() != null ? pathPlugins.getText().trim() : "");
            arPropertyManager.setProperty(
                    ARPropertyEnum.URL_PLUGINS.getValue(),
                    urlPlugins.getText() != null ? urlPlugins.getText().trim() : "");

            try {
                performInitializer.testConnection(
                        databaseChoiceBox.getValue(),
                        pathAccessDB.getText().trim(),
                        dbUrl.getText(),
                        dbUser.getText().trim(),
                        dbPwd.getText().trim());
            } catch (Exception error) {
                log.error("testConnection Error: " + error.getMessage());
                performMessage.errorMessage(
                        "Database connection Failed",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>An error occurred during the Database connection.</span>",
                        "<span style='font-weight: bold;'>" + databaseChoiceBox.getValue() + "</span>.",
                        "<span style='color: #E65100; font-weight: bold;'>Please ensure the Database connections are correct.</span>",
                        "<span style='font-style: italic;'>Details: " + error.getMessage() + "</span>",
                        0);

                return;
            }

            arPropertyManager.setProperty(ARPropertyEnum.DATABASE_TYPE.getValue(), databaseChoiceBox.getValue());

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
            viewBotJobListView.setItems(FXCollections.observableArrayList(performLists.getQuickBotJobs()));

            backupDBButton.setDisable(performLists.getListHomeBanking().isEmpty());
            homeBankingListView.setItems(FXCollections.observableArrayList(performLists.getListHomeBanking()));

            HomeBankingLoadDTO homeBank = performLists.getFirstHomeBanking();
            arNewHomeBankingScene.initialize(homeBank);

            try {

                if (!this.previousDB.equalsIgnoreCase(databaseChoiceBox.getValue())
                        || !this.previousDBUrl.equalsIgnoreCase(dbUrl.getText().trim())) {

                    errorMessage = performDataBase.loadQuickBotJobs();
                    if (errorMessage != null) {
                        performMessage.errorMessageOperationFailed(errorMessage);
                    }
                    this.previousDB = databaseChoiceBox.getValue();
                    this.previousDBUrl = dbUrl.getText().trim();
                }
                //                botJobList =
                // FXCollections.observableArrayList(FXCollections.observableArrayList(performLists.getQuickBotJobs()));
            } catch (Exception error) {
                throw error;
            }

            performMessage.showCustomModalDialogDragWin11(
                    "Configuration Saved \u2705",
                    "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>Configuration data saved and reloaded successfully.</span>",
                    "<span style='color: #1565C0;'>All settings have been applied.</span>",
                    "<span style='color: #455A64; font-style: italic;'>Database: <b>" + databaseChoiceBox.getValue()
                            + "</b></span>",
                    null,
                    false,
                    "OK",
                    null,
                    0);
        }
    }

    private void deleteAllDB() {
        if (isEnabledLicence && !checkLicense()) {
            return;
        }

        String dataBaseType = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);

        if (dataBaseType.equalsIgnoreCase(databaseChoiceBox.getValue().trim())) {

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
                            + databaseChoiceBox.getValue().trim(),
                    "<span style='color: #6A1B9A; font-weight: bold;'>Saved Database:</span> "
                            + dataBaseType + "<br/>"
                            + "<span style='color: #E65100; font-weight: bold;'>💡 Reminder:</span> Press the <span style='text-decoration: underline;'>Reload Configs</span> button to save and apply your database choice before continuing.",
                    false,
                    "OK",
                    null,
                    0);
            return;
        }

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

    /**
     * Translates the {@code DATABASE_TYPE} property value into a short lowercase
     * slug suitable for embedding in backup file names:
     *   <ul>
     *     <li>{@code "TEXT"}                 → {@code "sqlite"}</li>
     *     <li>{@code "Access"}               → {@code "access"}</li>
     *     <li>{@code "Postgres" / "PostGres"} → {@code "postgres"}</li>
     *     <li>anything else / null           → {@code "db"} (safe fallback)</li>
     *   </ul>
     * Kept forgiving so a stray casing in {@code config.properties} can't crash
     * the backup flow — the worst case is a file named {@code backup_db_all_...sql}.
     */
    private String dbDialectSlug(String dataBaseType) {
        if (dataBaseType == null) return "db";
        String t = dataBaseType.trim();
        if (t.equalsIgnoreCase("TEXT")) return "sqlite";
        if (t.equalsIgnoreCase("Access")) return "access";
        if (t.equalsIgnoreCase("Postgres") || t.equalsIgnoreCase("PostGres")) return "postgres";
        return "db";
    }

    private void showAlertTimer(
            Alert.AlertType alertType,
            String title,
            String header,
            String msg1,
            String msg2,
            String msg3,
            String msg4) {

        startAlertShowTimer();

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

            log.error("Cannot read/validate the License path/file. Error: " + error.getMessage());
            return false;
        }
    }

    private void closeAllScenes() {
        if (arNewBotJobScene != null) {
            arNewBotJobScene.closeModal();
        }
        if (arNewCommandScene != null) {
            arNewCommandScene.setSplitDTO(null);
            arNewCommandScene.closeModal();
        }
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
        if (arScannedElementScene != null) {
            arScannedElementScene.closeModal();
            arScannedElementScene.closeWebDrivers();
        }

        if (arWebDriver != null) {
            arWebDriver.closeAllDrivers();
            arWebDriver.closeCurrentDriver();
        }
    }

    public void startAlertShowTimer() {
        if (alertToShow == null) {

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
        }
    }

    public void runExportBotJob(int homeBankingId, int botJobId, String exportPath) {
        String dataBaseType = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"));

        String backupFileName = "backup_(BY_BOT_JOB)_" + dbDialectSlug(dataBaseType) + "_" + date + ".sql";

        ARExecution.DialogModal respModal = performMessage.showCustomModalDialogDragWin11(
                "Export Bot Job Confirmation",
                "<span style='font-weight: bold; color: #D32F2F;'>Are you sure you want to export this bot job?</span>",
                "Target database: <b>" + dataBaseType + "</b>",
                "<span style='color: #6A1B9A;'>home_banking_id:</span> <b>" + homeBankingId
                        + "</b> &nbsp;&nbsp; <span style='color: #6A1B9A;'>bot_job_id:</span> <b>" + botJobId + "</b>",
                "<span style='font-style: italic;'>Destination folder: <b>" + exportPath
                        + "</b><br/>Output file: <b>" + backupFileName
                        + "</b> — holds home_banking, bot_job, block, instruction, variable and reference rows scoped to this job.</span>",
                false,
                "Execute Export",
                "Cancel",
                0);

        if (respModal.equals(ARExecution.DialogModal.STOP)) {
            return;
        }

        try (Connection conn = performDataBase.getConnection()) {
            performBackup.initialize(conn);

            // One file per export: backup_(BY_BOT_JOB)_<dialect>_<date>.sql
            // Holds the six tables a bot-job export touches (home_banking,
            // bot_job, block, instruction, variable, reference) in -- TABLE:
            // sections, same envelope as the full DB single-file backup.
            String backupFilePath = exportPath + File.separator + backupFileName;
            ErrorMessage errorMessage =
                    performBackup.dumpBotJobToSingleFile(conn, backupFilePath, homeBankingId, botJobId);

            if (errorMessage == null) {
                performMessage.showCustomModalDialogDragWin11(
                        "Export Bot Job Success! ✅",
                        "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>Bot job exported successfully!</span>",
                        "<span style='color: #1565C0; font-weight: bold;'>All six tables (home_banking, bot_job, block, instruction, variable, reference) were dumped in FK-safe order.</span>",
                        "<span style='color: #6A1B9A; font-weight: bold;'>Database:</span> " + dataBaseType
                                + "<br/><span style='color: #6A1B9A; font-weight: bold;'>home_banking_id:</span> "
                                + homeBankingId
                                + " &nbsp;&nbsp; <span style='color: #6A1B9A; font-weight: bold;'>bot_job_id:</span> "
                                + botJobId
                                + "<br/><span style='color: #6A1B9A; font-weight: bold;'>Folder:</span> " + exportPath
                                + "<br/><span style='color: #6A1B9A; font-weight: bold;'>File:</span> "
                                + backupFileName,
                        "<span style='color: #E65100; font-weight: bold;'>💡 Tip:</span> Point <span style='text-decoration: underline;'>Import Bot Job</span> at this folder and date to bring it back.",
                        false,
                        "OK",
                        null,
                        0);
            } else {
                log.error("Export Bot Job error: " + errorMessage.getErrorMessage());
                performMessage.errorMessage(
                        "Export Bot Job error",
                        "<span style='color: #D32F2F; font-weight: bold; font-size: 1.1em;'>"
                                + errorMessage.getErrorTitle() + "</span> ❌",
                        "<span style='color: #E65100; font-weight: bold;'>Error Type:</span> "
                                + errorMessage.getErrorHeader(),
                        "<span style='font-style: italic;'>Detail:</span> " + errorMessage.getErrorMessage(),
                        null,
                        0);
            }

        } catch (SQLException ex) {
            log.info(ex.getMessage());
        }
    }

    public void runImportBotJob(
            Integer homeBankIdImported,
            String organizationName,
            Integer homeUrlIdImported,
            Integer botJobIdImported,
            LocalDate selectedDate,
            String importPath) {

        String formattedDate;
        if (selectedDate != null) {
            formattedDate = selectedDate.format(DateTimeFormatter.ofPattern("yyyy_MM_dd"));
            log.info("Selected import Bot Job date: " + formattedDate);
        } else {
            Label dateSelection = new Label(
                    "Please select a date to import from.\n" + "Check the database directory for available backups.");
            dateSelection.setWrapText(true);

            Alert alert = new Alert(Alert.AlertType.WARNING, null, ButtonType.OK);
            alert.setTitle("Import Bot Job  Warning");
            alert.setHeaderText("No Date Selected");
            alert.getDialogPane().setContent(dateSelection);
            alert.showAndWait();
            return;
        }

        String dataBaseType = arPropertyManager.getProperty(ARPropertyEnum.DATABASE_TYPE);

        Label newInstruction = new Label("DB IMPORT (ONLY BOT JOB)\nDatabase Selected: \"" + dataBaseType
                + "\" \nDatabase Folder : \"v4.2g Beta Test\"");
        newInstruction.setStyle("-fx-font-size: 18px; -fx-text-fill: red;");

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, null, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText("Are you sure you want to EXECUTE IMPORT (ONLY BOT JOB) (\"" + formattedDate + "\")?");
        alert.getDialogPane().setContent(newInstruction);

        ARExecution.DialogModal respModal = performMessage.showCustomModalDialogDragWin11(
                "Import Bot Job Confirmation",
                "<span style='font-weight: bold; color: #D32F2F;'>Are you sure you want to execute a import bot job?</span>",
                "The database type selected is: <span style='color: #1565C0; font-weight: bold;'>" + dataBaseType
                        + "</span>.",
                "<span style='color: #6A1B9A; font-weight: bold;'>Import date : " + formattedDate
                        + " will apply to the folder: </span>.",
                "<span style='font-style: italic;'>Details: " + importPath + "</span>",
                false,
                "Execute Import",
                "Cancel",
                0);

        if (!respModal.equals(ARExecution.DialogModal.STOP)) {
            try (Connection conn = performDataBase.getConnection()) {

                performBackup.initialize(conn);

                // Looks for backup_(BY_BOT_JOB)_<dialect>_<date>.sql in the
                // import folder. restoreBotJobFromSingleFile splits the file
                // into per-table temp pieces and runs the legacy restore chain
                // (bot_job → block → instruction → variable → update →
                // reference) with the provided home_banking / home_url /
                // bot_job id remaps, re-keying every FK via the old→new maps.
                String backupFilePath = importPath
                        + File.separator
                        + "backup_(BY_BOT_JOB)_"
                        + dbDialectSlug(dataBaseType)
                        + "_"
                        + formattedDate
                        + ".sql";
                ErrorMessage errorMessage = performBackup.restoreBotJobFromSingleFile(
                        conn,
                        backupFilePath,
                        homeBankIdImported,
                        homeUrlIdImported,
                        botJobIdImported,
                        organizationName);

                if (errorMessage == null) {
                    //                        closeAllScenes();

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

                    boolean isImportBotJob = homeBankIdImported != null;

                    if (viewBotJobListView == null) {
                        viewBotJobListView = arMainPane.getViewBotJobListView();
                    }

                    viewBotJobListView.setItems(FXCollections.observableArrayList(performLists.getQuickBotJobs()));

                    if (!isImportBotJob) {
                        backupDBButton.setDisable(
                                performLists.getListHomeBanking().isEmpty());

                        homeBankingListView.setItems(
                                FXCollections.observableArrayList(performLists.getListHomeBanking()));

                        HomeBankingLoadDTO homeBank = performLists.getFirstHomeBanking();
                        arNewHomeBankingScene.initialize(homeBank);
                    }

                    performMessage.showCustomModalDialogDragWin11(
                            "Import Bot Job! ✅",
                            "<span style='color: #2E7D32; font-weight: bold; font-size: 1.1em;'>Database restored successfully!</span>",
                            "<span style='color: #1565C0; font-weight: bold;'>Now you can start to use your database!</span>",
                            "<span style='color: #6A1B9A; font-weight: bold;'>Database:</span> " + dataBaseType,
                            "<span style='color: #E65100; font-weight: bold;'>💡 Don't forget:</span> Press the <span style='text-decoration: underline;'>Reload DB</span> button to refresh your data!",
                            false,
                            "OK",
                            null,
                            0);
                } else {
                    log.error("Import Bot Job error: " + errorMessage.getErrorMessage());
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
    }
}
