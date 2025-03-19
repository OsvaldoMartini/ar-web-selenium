package com.allinweb.ch.component.pane;

import com.allinweb.ch.component.listCell.ARCellFactory;
import com.allinweb.ch.component.listCell.BotJobListCell;
import com.allinweb.ch.component.model.BotJobLoadDTO;
import com.allinweb.ch.component.pane.base.ARPane;
import com.allinweb.ch.component.scene.ARAlertScene;
import com.allinweb.ch.component.scene.ARConfigurationScene;
import com.allinweb.ch.component.scene.ARInfoScene;
import com.allinweb.ch.component.scene.ARNewBotJobScene;
import com.allinweb.ch.component.scene.ARSaveCloneScene;
import com.allinweb.ch.component.scene.ARViewBotJobScene;
import com.allinweb.ch.control.ARComponentBuilder;
import com.allinweb.ch.core.ARSharedResources;
import com.allinweb.ch.driver.ARWebDriver;
import com.allinweb.ch.facade.PerformActions;
import com.allinweb.ch.facade.PerformDataBase;
import com.allinweb.ch.facade.PerformMessage;
import com.allinweb.ch.facade.PerformPreLoad;
import com.allinweb.ch.persistence.ConfigUserDTO;
import com.allinweb.ch.util.ARConstants;
import com.allinweb.ch.util.ARLogger;
import com.allinweb.ch.util.ARPropertyEnum;
import com.allinweb.ch.util.ARPropertyManager;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.*;
import org.openqa.selenium.WebDriver;

public class ARMainPane extends ARPane {

    //    private static final ARSharedResources dbResource;
    private static final PerformDataBase performDataBase;
    private static final PerformMessage performMessage;
    private static final PerformActions performActions;
    private static final ARConfigurationScene arConfigurationScene;
    private static final ARViewBotJobScene arViewBotJobScene;
    private static final ARNewBotJobScene arNewBotJobScene;
    private static final ARWebDriver arWebDriver;
    private static final PerformPreLoad performPreLoad;

    private static String previousDB;
    private ObservableList<BotJobLoadDTO> botJobList = FXCollections.observableArrayList();
    private ObservableList<WebDriver> webDriverList = FXCollections.observableArrayList();

    private static final String CONNECTION_TYPE = "jdbc:ucanaccess://";
    private static final String CONNECTION_PARAMETERS = ";memory=false;newDatabaseVersion=V2010";
    // Postgres
    private static boolean POSTGRES_DB = false;

    // Static block to initialize
    static {
        //        dbResource = ARSharedResources.getInstance();
        arNewBotJobScene = ARNewBotJobScene.getInstance();
        performDataBase = PerformDataBase.getInstance();
        performMessage = PerformMessage.getInstance();
        performActions = PerformActions.getInstance();
        arConfigurationScene = ARConfigurationScene.getInstance();
        arViewBotJobScene = ARViewBotJobScene.getInstance();
        arWebDriver = ARWebDriver.getInstance();
        performPreLoad = PerformPreLoad.getInstance();
    }

    private static final ARComponentBuilder builder = new ARComponentBuilder();
    private Properties properties = new Properties();

    // UI components
    Button newBotJobButton;
    Button cloneBotJobButton;
    // Button viewBotJobButton;
    Button configureButton;
    Button infoButton;
    Button editBotJobButton;
    Button launchBotJobButton;
    Button exitButton;
    HBox buttonPane;
    VBox panelPane;

    GridPane header = new GridPane();

    ListView<BotJobLoadDTO> viewBotJobListView = new ListView<>();

    public ARMainPane() {
        String pathDB = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.FOLDER_PATH_DB);
        String dataBaseType = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.DATABASE_TYPE);
        performDataBase.initialize(dataBaseType);

        if (dataBaseType != null && dataBaseType.equalsIgnoreCase("POSTGRES")) {
            POSTGRES_DB = true;

            if (!performDataBase.doesInstructionTableExist()) {
                performDataBase.initializeMainDatabasePostgres();
            }
        } else {
            POSTGRES_DB = false;
        }

        if (!POSTGRES_DB) {
            String dbPath = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.FOLDER_PATH_DB);
            String dbUrl = CONNECTION_TYPE + dbPath + ARConstants.FILE_NAME_DB + CONNECTION_PARAMETERS;

            File dbFile = new File(dbPath + ARConstants.FILE_NAME_DB);
            if (!dbFile.exists()) {
                performDataBase.initializeMainDatabaseAccess(dbUrl, dbFile);
            } else {
                ARLogger.getInstance(ARMainPane.class)
                        .info(String.format("Database '%s' already exists!", dbFile.getName()));
            }
            //            performDataBase.updatePossibleMigrationColumnsTable(dbUrl, dbFile);

        }

        //        dbResource.setPreviousDB(previousDB);
        arConfigurationScene.initialize(previousDB);

        if (pathDB == null || pathDB.isBlank()) {
            arConfigurationScene.show();
            new ARAlertScene(
                    Alert.AlertType.WARNING,
                    "Configuration Needed",
                    "Please configure the application before use.",
                    ButtonType.OK);
        }
    }

    private void loadConfigDB() {

        ARSharedResources resources = ARSharedResources.getInstance();
        if (resources != null) {
            List<ConfigUserDTO> configurationList =
                    ARSharedResources.getInstance().loadUserConfig();
            setProperty(ARPropertyEnum.FOLDER_PATH_EXCEL.getValue(), "");
            setProperty(ARPropertyEnum.FOLDER_PATH_LOG.getValue(), "");
            setProperty(ARPropertyEnum.FOLDER_PATH_EXPORT.getValue(), "");
            //            setProperty(ARPropertyEnum.FILE_NAME_EXPORT.getValue(), "");
            setProperty(
                    ARPropertyEnum.FOLDER_PATH_JAVA.getValue(),
                    ARConstants.CURRENT_PATH + ARConstants.DEFAULT_PATH_JAVA);
            setProperty(
                    ARPropertyEnum.FOLDER_PATH_JAVA_FX.getValue(),
                    ARConstants.CURRENT_PATH + ARConstants.DEFAULT_PATH_JAVA_FX);
            setProperty(ARPropertyEnum.FOLDER_PATH_DB.getValue(), "");
            setProperty(ARPropertyEnum.FOLDER_PATH_REPORT.getValue(), "");
            setProperty(ARPropertyEnum.PATH_ENGINE.getValue(), ARConstants.CURRENT_PATH);
            setProperty(ARPropertyEnum.LOG_LEVEL.getValue(), Level.ALL.getName());
            setProperty(ARPropertyEnum.BROWSER.getValue(), ARConstants.CHROME);
            setProperty(ARPropertyEnum.WEBDRIVER_PAGE_UPDATE_TIMEOUT_SEC.getValue(), "60");
            setProperty(ARPropertyEnum.WEBDRIVER_INTERACTION_TIMEOUT_SEC.getValue(), "60");
            setProperty(ARPropertyEnum.DEFAULT_INSTRUCTION_STOP_SECONDS.getValue(), "15");
            setProperty(
                    ARPropertyEnum.WEBDRIVER_EXT_REFERENCE.getValue(),
                    "test-id='web-banking-payment-core.payment-details.external-reference'");
        }
    }

    @Override
    public void initUIComponents() {
        newBotJobButton = builder.buildButton(
                "New", ARConstants.SPACE_M, ARConstants.ICON_NEW, ARConstants.SPACE_M, new Insets(8, 10, 8, 10));
        cloneBotJobButton = builder.buildButton(
                "Clone Job", ARConstants.SPACE_M, ARConstants.ICON_SAVE, ARConstants.SPACE_M, new Insets(8, 10, 8, 10));
        configureButton = builder.buildButton(
                "Config", ARConstants.SPACE_M, ARConstants.ICON_CONFIG, ARConstants.SPACE_M, new Insets(8, 10, 8, 10));
        infoButton = builder.buildButton(
                "Info", ARConstants.SPACE_M, ARConstants.ICON_INFO, ARConstants.SPACE_M, new Insets(8, 10, 8, 10));
        editBotJobButton = builder.buildButton(
                "Open Job", ARConstants.SPACE_L, ARConstants.ICON_EDIT, ARConstants.SPACE_M, new Insets(8, 10, 8, 10));
        launchBotJobButton = builder.buildButton(
                "Launch", ARConstants.SPACE_L, ARConstants.ICON_PLAY, ARConstants.SPACE_M, new Insets(8, 10, 8, 10));
        exitButton = builder.buildButton(
                "Exit", ARConstants.SPACE_L, ARConstants.ICON_CROSS, ARConstants.SPACE_M, new Insets(8, 10, 8, 10));

        buttonPane = new HBox(
                newBotJobButton,
                cloneBotJobButton,
                configureButton,
                infoButton,
                launchBotJobButton,
                editBotJobButton,
                exitButton);
        buttonPane.maxHeight(ARConstants.SPACE_L);
        AnchorPane.setTopAnchor(buttonPane, ARConstants.SPACE_ZERO);
        AnchorPane.setLeftAnchor(buttonPane, ARConstants.SPACE_ZERO);
        AnchorPane.setRightAnchor(buttonPane, ARConstants.SPACE_ZERO);
        buttonPane.setAlignment(Pos.TOP_CENTER);

        initHeader();

        //        ObservableList<BotJobLoadDTO> botJobList =
        // ARSharedResources.getInstance().getEntityList(BotJobDTO.class);
        botJobList.addAll(performDataBase.loadAllBotJobs());
        viewBotJobListView.setItems(botJobList);
        viewBotJobListView.setCellFactory(new ARCellFactory<>(
                BotJobListCell.class,
                arViewBotJobScene,
                arWebDriver,
                performDataBase,
                performActions,
                performMessage,
                performPreLoad,
                botJobList,
                webDriverList)::call);
        arNewBotJobScene.initialize(
                arViewBotJobScene,
                arWebDriver,
                performDataBase,
                performActions,
                performMessage,
                botJobList,
                webDriverList);
        //        viewBotJobListView.setMaxSize(800D, 580D);

        arWebDriver.initialize(performMessage, performPreLoad);

        panelPane = new VBox(buttonPane, header, viewBotJobListView);
        VBox.setMargin(viewBotJobListView, new Insets(0, 10D, 10D, 10D));
        VBox.setVgrow(viewBotJobListView, Priority.ALWAYS);
        HBox.setHgrow(viewBotJobListView, Priority.ALWAYS);

        AnchorPane.setTopAnchor(panelPane, ARConstants.SPACE_ZERO);
        AnchorPane.setBottomAnchor(panelPane, ARConstants.SPACE_ZERO);
        AnchorPane.setLeftAnchor(panelPane, ARConstants.SPACE_ZERO);
        AnchorPane.setRightAnchor(panelPane, ARConstants.SPACE_ZERO);
    }

    @Override
    public void initUIBehaviour() {
        newBotJobButton.setOnMouseClicked(e -> {
            arNewBotJobScene.initialize(
                    arViewBotJobScene,
                    arWebDriver,
                    performDataBase,
                    performActions,
                    performMessage,
                    botJobList,
                    webDriverList);
            arNewBotJobScene.showModal();
            botJobList.clear();
            botJobList.addAll(performDataBase.loadAllBotJobs());
            viewBotJobListView.setItems(botJobList);
        });

        cloneBotJobButton.setOnMouseClicked(e -> {
            var selecBotJobDTO = viewBotJobListView.getSelectionModel().getSelectedItem();
            if (selecBotJobDTO != null) {
                new ARSaveCloneScene(selecBotJobDTO, performDataBase.loadAllBotJobs()).showModal();
                ObservableList<BotJobLoadDTO> botJobList =
                        FXCollections.observableArrayList(performDataBase.loadAllBotJobs());
                viewBotJobListView.setItems(botJobList);
                viewBotJobListView.refresh();

            } else {
                performMessage.errorMessage("Select a Bot Job", "There is NOT a Job Selected", null, null, null, 0);
                return;
            }
        });

        configureButton.setOnMouseClicked(e -> {
            arConfigurationScene.showModal();
            performDataBase.changeDbConnection();
            ObservableList<BotJobLoadDTO> botJobList =
                    FXCollections.observableArrayList(performDataBase.loadAllBotJobs());
            viewBotJobListView.setItems(botJobList);
        });
        infoButton.setOnMouseClicked(e -> new ARInfoScene().showModal());
        exitButton.setOnMouseClicked(e -> {
            //            Platform.exit();
            Runtime.getRuntime().addShutdownHook(new Thread(this::closeWebDrivers));
            System.exit(0);
        });

        editBotJobButton.setOnMouseClicked(e -> {
            BotJobLoadDTO selecBotJobDTO =
                    viewBotJobListView.getSelectionModel().getSelectedItem();

            if (selecBotJobDTO != null) {
                try {
                    Platform.runLater(() -> {
                        // new ARViewBotJobScene(selecBotJobDTO).showModal();

                        arViewBotJobScene.initialize(
                                arWebDriver,
                                performDataBase,
                                performActions,
                                performMessage,
                                performPreLoad,
                                selecBotJobDTO,
                                botJobList,
                                webDriverList);
                        arViewBotJobScene.show();

                        // new Alert(AlertType.WARNING, "Error" + selecBotJobDTO.getName()).show();
                    });

                } catch (Exception e2) {
                    new Alert(AlertType.WARNING, "Error" + selecBotJobDTO.getName() + "  " + e2.getMessage())
                            .show(); // TODO: handle exception
                }
                // new ARMoveBlockScene(selecBotJobDTO.getBlocks().get(0));
            } else {
                performMessage.errorMessage("Select a Bot Job", "There is NOT a Job Selected", null, null, null, 0);
                return;
            }
        });

        launchBotJobButton.setOnMouseClicked(e -> {
            {
                var selecBotJobDTO = viewBotJobListView.getSelectionModel().getSelectedItem();
                if (selecBotJobDTO != null) {
                    ARPropertyManager managerProps = ARPropertyManager.getInstance();
                    String enginePath = managerProps.getProperty(ARPropertyEnum.PATH_ENGINE) + "\\AR_Web_Engine.jar";
                    String excelPath = managerProps.getProperty(ARPropertyEnum.FOLDER_PATH_EXCEL);
                    excelPath = excelPath + "\\" + selecBotJobDTO.getName() + ".xlsx";
                    if (!new File(excelPath).exists()) {
                        new ARAlertScene(
                                Alert.AlertType.WARNING,
                                "Missing file excel",
                                "Please generate and compile the data of the file excel first before launching the bot job",
                                ButtonType.OK);
                    }

                    String[] command = new String[] {
                        "cmd.exe",
                        "/c",
                        ".\\java\\bin\\java.exe",
                        "-jar",
                        "\"" + enginePath + "\"",
                        "execute/j",
                        String.valueOf(selecBotJobDTO.getHomeBankingLoadDTO().getId()),
                        String.valueOf(selecBotJobDTO.getId()),
                        "\"" + excelPath + "\"",
                        "-c",
                        ARPropertyManager.getConfigurationFileName()
                    };
                    ProcessBuilder processBuilder = new ProcessBuilder(command);
                    processBuilder.directory(new File(ARConstants.CURRENT_PATH));
                    String logPath = ARPropertyManager.getInstance().getProperty(ARPropertyEnum.FOLDER_PATH_LOG);
                    File output = new File(logPath + "\\engine_debug_log_output.log");
                    File error = new File(logPath + "\\engine_debug_log_error.log");
                    File input = new File(logPath + "\\engine_debug_log_input.log");
                    List<File> files = new ArrayList<>();
                    files.add(output);
                    files.add(error);
                    files.add(input);
                    for (File file : files) {
                        if (!file.exists()) {
                            try {
                                file.createNewFile();
                            } catch (IOException ex) {
                                ARLogger.getInstance(ARScannedElementPane.class).fine("Error : " + ex);
                            }
                        }
                    }
                    processBuilder.redirectOutput(output);
                    processBuilder.redirectError(error);
                    processBuilder.redirectInput(input);
                    try {
                        processBuilder.start();
                    } catch (IOException ex) {
                        ARLogger.getInstance(ARScannedElementPane.class).fine("Error : " + ex);
                    }
                } else {
                    performMessage.errorMessage("Select a Bot Job", "There is NOT a Job Selected", null, null, null, 0);
                    return;
                }
            }
        });
    }

    private void initHeader() {
        header.setMaxHeight(ARConstants.SPACE_M);

        // Create an HBox for the header and set spacing
        HBox headerHBox = new HBox(10);
        headerHBox.setPadding(new Insets(5D, 10D, 5D, 20D)); // Added padding to the left (20D)

        // Create labels for each column header
        Label nameLabel = new Label("Name");
        nameLabel.setMinWidth(150); // Set minimum width
        nameLabel.setMaxWidth(150); // Set maximum width
        nameLabel.setWrapText(true);

        Label descriptionLabel = new Label("Description");
        descriptionLabel.setMinWidth(150); // Set minimum width
        descriptionLabel.setMaxWidth(150); // Set maximum width
        descriptionLabel.setWrapText(true);

        Label environmentLabel = new Label("Environment");
        environmentLabel.setMinWidth(100); // Set minimum width
        environmentLabel.setMaxWidth(100); // Set maximum width
        environmentLabel.setWrapText(true);

        Label statusLabel = new Label("Status");
        statusLabel.setMinWidth(50); // Set minimum width
        statusLabel.setMaxWidth(50); // Set maximum width
        statusLabel.setWrapText(true);

        Label actionsLabel = new Label("Actions");
        actionsLabel.setMinWidth(50); // Set minimum width
        actionsLabel.setMaxWidth(50); // Set maximum width
        actionsLabel.setWrapText(true);

        // Create spacers
        Region spacer1 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);
        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);
        Region spacerStatus = new Region();
        spacerStatus.setMinWidth(15);
        HBox.setHgrow(spacerStatus, Priority.ALWAYS);
        Region spacerAction = new Region();
        spacerAction.setMinWidth(5);
        HBox.setHgrow(spacerAction, Priority.ALWAYS);
        Region spacer5 = new Region();
        HBox.setHgrow(spacer5, Priority.ALWAYS);

        // Add labels and spacers to the HBox
        headerHBox
                .getChildren()
                .addAll(
                        nameLabel, spacer1,
                        descriptionLabel, spacer2,
                        environmentLabel, spacerStatus,
                        statusLabel, spacerAction,
                        actionsLabel, spacer5);

        // Set HBox as the header
        header.getChildren().clear(); // Clear any existing children (if any)
        header.getChildren().add(headerHBox); // Add the new header with labels and spacers

        // Set margins for the header
        VBox.setMargin(headerHBox, new Insets(5D, 10D, 5D, 10D));
    }

    public void setProperty(String propertyName, String value) {
        this.properties.setProperty(propertyName, value);
    }

    @Override
    public Pane getPaneReference() {
        return new AnchorPane(panelPane);
    }

    public ObservableList<WebDriver> getWebDriverList() {
        return webDriverList;
    }

    // Method to add WebDriver instances
    public void addWebDriver(WebDriver driver) {
        Platform.runLater(() -> webDriverList.add(driver));
    }

    // Method to close all WebDriver instances
    private void closeWebDrivers() {
        for (WebDriver driver : webDriverList) {
            try {
                driver.quit();
                ARLogger.getInstance(ARMainPane.class).info("WebDriver closed.");
            } catch (Exception e) {
                ARLogger.getInstance(ARMainPane.class).warning("Error closing WebDriver: " + e.getMessage());
            }
        }
        Platform.runLater(() -> webDriverList.clear());
    }
}
